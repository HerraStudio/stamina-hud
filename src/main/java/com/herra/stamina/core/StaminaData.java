package com.herra.stamina.core;

import com.herra.stamina.api.StaminaModifier;
import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每玩家、服务端权威的体力数据。以运行时 Attachment 存储（不落盘）：
 * 每次进入战局都是满体力开局，符合搜打撤玩法。
 *
 * <p>透支锁（exhausted lock）：体力归零后进入锁定状态，恢复速度减半、
 * 禁止疾跑，直到回气到 {@code exhausted_release_threshold} 才解除。</p>
 *
 * <p>修改器（{@link StaminaModifier}）：医药/增益类模组通过
 * {@code StaminaAPI.applyModifier} 挂载的定时数值修改，这里聚合出
 * 最终上限/消耗倍率/恢复速度。</p>
 */
public class StaminaData {

    private float stamina;
    /** 距上一次消耗过去的 tick 数。 */
    private int ticksSinceConsumption = Integer.MAX_VALUE / 2;
    /** 透支锁定：归零触发，恢复到解锁阈值解除。 */
    private boolean exhaustedLock;
    /** 上一次同步给客户端的值（节流用）。 */
    private float syncedStamina = Float.NaN;
    private boolean syncedLock;
    private boolean syncedSprintBlocked;
    /** 同步冷却（tick）。 */
    private int syncCooldown;

    // ---- 移动检测（服务端玩家的 deltaMovement 不随输入更新，必须用位置差） ----
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private boolean movedHorizontally;
    private boolean movedVertically;

    // ---- 生效中的修改器（运行时，id -> 实例） ----
    private final Map<String, ActiveModifier> modifiers = new LinkedHashMap<>();

    public StaminaData() {
        // 注意：Attachment 惰性创建时机早于配置加载完成的情况是可能的，
        // 这里用安全默认值；真实上限由 getMaxStamina() 每次读配置。
        this.stamina = 100.0F;
    }

    /** 服务端每 tick 的修改器条目（修改器 + 剩余时间）。 */
    public static final class ActiveModifier {
        public final StaminaModifier modifier;
        public int remainingTicks;

        ActiveModifier(StaminaModifier modifier) {
            this.modifier = modifier;
            this.remainingTicks = modifier.durationTicks();
        }
    }

    // ------------------------------------------------------------------ 读取

    public float getStamina() {
        return stamina;
    }

    /** 最终上限 = (配置基础值 + Σ加算) × Π乘算，至少为 1。 */
    public float getMaxStamina() {
        float max = StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA);
        if (!modifiers.isEmpty()) {
            float bonus = 0.0F;
            float multiplier = 1.0F;
            for (ActiveModifier active : modifiers.values()) {
                bonus += active.modifier.maxStaminaBonus();
                multiplier *= active.modifier.maxStaminaMultiplier();
            }
            max = (max + bonus) * multiplier;
        }
        return Math.max(1.0F, max);
    }

    public float getRatio() {
        float max = getMaxStamina();
        return max <= 0 ? 1.0F : stamina / max;
    }

    public boolean isExhaustedLock() {
        return exhaustedLock;
    }

    public boolean isFull() {
        return stamina >= getMaxStamina() - 1.0e-4F;
    }

    /** 低体力禁跑判定（服务端权威，同时同步给客户端镜像处理）。
     * 透支期间的禁跑受 penalty.exhausted_block_sprint 配置控制（严格模式）。 */
    public boolean isSprintBlocked() {
        if (exhaustedLock) {
            return StaminaServerConfig.EXHAUSTED_BLOCK_SPRINT.get();
        }
        return stamina <= StaminaServerConfig.f(StaminaServerConfig.SPRINT_STOP_THRESHOLD);
    }

    public int getTicksSinceConsumption() {
        return ticksSinceConsumption;
    }

    // ------------------------------------------------------------------ 修改器

    /** 挂载/刷新修改器（同 id 覆盖）。返回 true = 上限发生变化需要钳制。 */
    public boolean applyModifier(StaminaModifier modifier) {
        modifiers.put(modifier.id(), new ActiveModifier(modifier));
        return clampToMax() | forceResync();
    }

    /** 移除指定 id 的修改器。返回 true = 上限发生变化需要钳制。 */
    public boolean clearModifier(String id) {
        boolean removed = modifiers.remove(id) != null;
        if (removed) {
            return clampToMax() | forceResync();
        }
        return false;
    }

    /** 清空全部修改器（死亡/新战局/解毒等场景）。返回 true = 上限变化。 */
    public boolean clearModifiers() {
        if (modifiers.isEmpty()) {
            return false;
        }
        modifiers.clear();
        return clampToMax() | forceResync();
    }

    /** 服务端每 tick 倒计时，到期自动移除。返回 true = 有修改器到期（上限可能变化）。 */
    public boolean tickModifiers() {
        if (modifiers.isEmpty()) {
            return false;
        }
        boolean expired = false;
        var iterator = modifiers.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveModifier active = iterator.next().getValue();
            if (active.remainingTicks < 0) {
                continue; // 永久
            }
            if (--active.remainingTicks <= 0) {
                iterator.remove();
                expired = true;
            }
        }
        if (expired) {
            return clampToMax() | forceResync();
        }
        return false;
    }

    /** 只读快照。 */
    public List<ActiveModifier> getActiveModifiers() {
        return new ArrayList<>(modifiers.values());
    }

    public boolean hasModifiers() {
        return !modifiers.isEmpty();
    }

    /** 所有消耗的聚合乘算倍率（Π drainMultiplier）。 */
    public float getDrainMultiplier() {
        float multiplier = 1.0F;
        for (ActiveModifier active : modifiers.values()) {
            multiplier *= active.modifier.drainMultiplier();
        }
        return multiplier;
    }

    /** 恢复速度聚合加算（每秒）。 */
    public float getRegenBonus() {
        float bonus = 0.0F;
        for (ActiveModifier active : modifiers.values()) {
            bonus += active.modifier.regenBonus();
        }
        return bonus;
    }

    /** 恢复速度聚合乘算。 */
    public float getRegenMultiplier() {
        float multiplier = 1.0F;
        for (ActiveModifier active : modifiers.values()) {
            multiplier *= active.modifier.regenMultiplier();
        }
        return multiplier;
    }

    // ------------------------------------------------------------------ 移动检测

    /**
     * 服务端玩家移动检测：记录本 tick 位置并返回是否发生水平位移。
     *
     * <p>不能用 {@code player.getDeltaMovement()}：服务端的移动由
     * {@code handleMovePlayer -> Entity.move} 应用，只改坐标不写
     * deltaMovement（只有撞墙/击退等才写），因此输入移动时它恒为 0。</p>
     *
     * <p>垂直位移单独记录（{@link #movedVertically()}），供游泳消耗判定
     * 使用（下潜/上浮时水平位移几乎为 0）。阈值取 0.05 格/tick，
     * 排除水中被动下沉（约 0.02~0.03 格/tick）与地面抖动。</p>
     */
    public boolean updateMovement(Player player) {
        double dx = player.getX() - lastX;
        double dy = player.getY() - lastY;
        double dz = player.getZ() - lastZ;
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        if (Double.isNaN(dx) || Double.isNaN(dy) || Double.isNaN(dz)) {
            movedHorizontally = movedVertically = true; // 首次记录无法比较，按“在移动”处理
            return true;
        }
        double hSqr = dx * dx + dz * dz;
        // 位置瞬移（传送/重生，>8 格）不算正常移动，但也不至于误判为静止
        movedHorizontally = hSqr <= 64.0 && hSqr > 1.0e-4;
        double vSqr = dy * dy;
        movedVertically = vSqr <= 64.0 && vSqr > 0.0025; // |dy| > 0.05 格
        return movedHorizontally;
    }

    /** 本 tick 是否有垂直主动位移（游泳上浮/下潜判定用）。 */
    public boolean movedVertically() {
        return movedVertically;
    }

    // ------------------------------------------------------------------ 写入（仅 StaminaManager / StaminaAPI 走这里，保证事件与同步一致）

    void setStaminaRaw(float value) {
        float max = getMaxStamina();
        this.stamina = Math.max(0.0F, Math.min(max, value));
    }

    void setTicksSinceConsumption(int ticks) {
        this.ticksSinceConsumption = ticks;
    }

    void setExhaustedLock(boolean value) {
        this.exhaustedLock = value;
    }

    /** 钳制到当前上限（修改器缩减上限时用）。返回 true = 数值被钳制。 */
    boolean clampToMax() {
        float max = getMaxStamina();
        if (stamina > max) {
            stamina = max;
            return true;
        }
        return false;
    }

    /** 强制下一次同步（上限变化等）。 */
    boolean forceResync() {
        syncedStamina = Float.NaN;
        return true;
    }

    // ------------------------------------------------------------------ 同步节流

    boolean shouldSync() {
        if (Float.isNaN(syncedStamina)) {
            return true;
        }
        float delta = Math.abs(stamina - syncedStamina);
        boolean flagsChanged = syncedLock != exhaustedLock
                || syncedSprintBlocked != isSprintBlocked();
        return flagsChanged || delta >= StaminaServerConfig.f(StaminaServerConfig.SYNC_DELTA);
    }

    void markSynced() {
        this.syncedStamina = stamina;
        this.syncedLock = exhaustedLock;
        this.syncedSprintBlocked = isSprintBlocked();
    }

    void tickCooldown() {
        if (syncCooldown > 0) {
            syncCooldown--;
        }
    }

    boolean cooldownReady() {
        return syncCooldown == 0;
    }

    void setSyncCooldown(int cooldown) {
        this.syncCooldown = cooldown;
    }

    /** 发送同步包（调用方需保证在服务端主线程）。 */
    void sendTo(ServerPlayer player) {
        com.herra.stamina.network.StaminaSyncPayload.sendTo(player, stamina, getMaxStamina(),
                exhaustedLock, isSprintBlocked());
        markSynced();
    }

    // ------------------------------------------------------------------ NBT 快照（供战局系统/数据库等外部工具用）

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("stamina", stamina);
        tag.putBoolean("exhausted_lock", exhaustedLock);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        if (tag.contains("stamina")) {
            setStaminaRaw(tag.getFloat("stamina"));
        }
        if (tag.contains("exhausted_lock")) {
            exhaustedLock = tag.getBoolean("exhausted_lock");
        }
        syncedStamina = Float.NaN; // 强制重新同步
    }

    /** 两侧通用的数据访问入口。 */
    public static StaminaData of(net.minecraft.world.entity.player.Player player) {
        return player.getData(ModAttachments.STAMINA.get());
    }
}
