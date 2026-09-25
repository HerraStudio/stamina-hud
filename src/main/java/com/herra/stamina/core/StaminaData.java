package com.herra.stamina.core;

import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 每玩家、服务端权威的体力数据。以运行时 Attachment 存储（不落盘）：
 * 每次进入战局都是满体力开局，符合搜打撤玩法。
 *
 * <p>透支锁（exhausted lock）：体力归零后进入锁定状态，恢复速度减半、
 * 禁止疾跑，直到回气到 {@code exhausted_release_threshold} 才解除。</p>
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

    public StaminaData() {
        // 注意：Attachment 惰性创建时机早于配置加载完成的情况是可能的，
        // 这里用安全默认值；真实上限由 getMaxStamina() 每次读配置。
        this.stamina = 100.0F;
    }

    // ------------------------------------------------------------------ 读取

    public float getStamina() {
        return stamina;
    }

    public float getMaxStamina() {
        return StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA);
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

    /** 低体力禁跑判定（服务端权威，同时同步给客户端镜像处理）。 */
    public boolean isSprintBlocked() {
        return exhaustedLock || stamina <= StaminaServerConfig.f(StaminaServerConfig.SPRINT_STOP_THRESHOLD);
    }

    public int getTicksSinceConsumption() {
        return ticksSinceConsumption;
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
