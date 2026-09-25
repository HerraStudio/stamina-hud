package com.herra.stamina.api;

import com.herra.stamina.config.StaminaServerConfig;
import com.herra.stamina.core.ModAttachments;
import com.herra.stamina.core.StaminaData;
import com.herra.stamina.core.StaminaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HERRA 生态的公开入口 —— 其他模组需要的一切都在这里：
 * 读体力、付自定义消耗、改写消耗规则、响应透支/回气、判定能否疾跑。
 *
 * <p>本模组自身从不调用 GWO 枪械 / 淘汰播报 / 战局系统 —— 集成是单向的，
 * 因此每个模组都可以独立启用/禁用。</p>
 *
 * <p>服务端数值权威。客户端调用读取类方法时，返回本地玩家 HUD 的
 * 插值显示值（对 HUD 类模组足够精确）；传 ServerPlayer 则读真实值。</p>
 *
 * <p>典型用法（重甲模组加大疾跑消耗）：</p>
 * <pre>{@code
 * StaminaAPI.registerDrainModifier((player, action, cost) ->
 *     action == StaminaAction.SPRINT && isWearingHeavyArmor(player) ? cost * 1.35F : cost);
 * }</pre>
 *
 * <p>定时增益（医药模组等）请用 {@link StaminaModifier}：</p>
 * <pre>{@code
 * StaminaAPI.applyModifier(player, StaminaModifier.builder("herra_med:stimulant")
 *         .drainMultiplier(0.5F)      // 消耗减半（疾跑/跳跃/游泳全部生效）
 *         .maxStaminaBonus(30.0F)     // 体力上限 +30（体力条变长）
 *         .regenMultiplier(1.5F)      // 恢复提速 50%
 *         .durationSeconds(90)        // 90 秒后自动失效
 *         .build());
 * }</pre>
 */
public final class StaminaAPI {

    // ------------------------------------------------------------------ 消耗规则修改器

    private static final List<StaminaDrainModifier> DRAIN_MODIFIERS = new CopyOnWriteArrayList<>();

    /** 由客户端 HUD 安装，让公共代码能读到本地显示值而不引用客户端类。 */
    private static volatile ClientDisplayBridge clientDisplay;

    public static void registerDrainModifier(StaminaDrainModifier modifier) {
        if (modifier != null) {
            DRAIN_MODIFIERS.add(modifier);
        }
    }

    public static void unregisterDrainModifier(StaminaDrainModifier modifier) {
        DRAIN_MODIFIERS.remove(modifier);
    }

    public static List<StaminaDrainModifier> getDrainModifiers() {
        return DRAIN_MODIFIERS;
    }

    /** 按注册顺序依次应用所有修改器。 */
    public static float applyDrainModifiers(Player player, StaminaAction action, float baseCost) {
        float cost = baseCost;
        for (StaminaDrainModifier modifier : DRAIN_MODIFIERS) {
            cost = modifier.modify(player, action, cost);
        }
        return Math.max(0.0F, cost);
    }

    /** 内部：客户端 HUD 安装显示桥。 */
    public static void installClientDisplay(ClientDisplayBridge bridge) {
        clientDisplay = bridge;
    }

    // ------------------------------------------------------------------ 定时修改器（医药/增益类模组）

    /**
     * 挂载/刷新一个定时修改器（同 id 覆盖：刷新剩余时间与数值）。
     * 服务端调用；生效期间自动影响体力上限、全部消耗与恢复速度，
     * 到期自动失效并重新同步 HUD，调用方无需自己计时。
     */
    public static void applyModifier(Player player, StaminaModifier modifier) {
        requireServer(player, "applyModifier");
        StaminaData.of(player).applyModifier(modifier);
        if (player instanceof ServerPlayer serverPlayer) {
            StaminaManager.syncNow(serverPlayer);
        }
    }

    /** 移除指定 id 的修改器（如药效被解药打断）。 */
    public static void clearModifier(Player player, String id) {
        requireServer(player, "clearModifier");
        StaminaData.of(player).clearModifier(id);
        if (player instanceof ServerPlayer serverPlayer) {
            StaminaManager.syncNow(serverPlayer);
        }
    }

    /** 清空该玩家全部修改器（死亡/新战局/洗胃等场景）。 */
    public static void clearModifiers(Player player) {
        requireServer(player, "clearModifiers");
        StaminaData.of(player).clearModifiers();
        if (player instanceof ServerPlayer serverPlayer) {
            StaminaManager.syncNow(serverPlayer);
        }
    }

    /** 生效中修改器的只读快照（不包含剩余时间）。 */
    public static List<StaminaModifier> getActiveModifiers(Player player) {
        List<StaminaModifier> list = new java.util.ArrayList<>();
        for (StaminaData.ActiveModifier active : StaminaData.of(player).getActiveModifiers()) {
            list.add(active.modifier);
        }
        return list;
    }

    /** 生效中修改器 + 剩余 tick（-1 = 永久）。UI/指令展示用。 */
    public static List<ModifierEntry> getActiveModifierEntries(Player player) {
        List<ModifierEntry> list = new java.util.ArrayList<>();
        for (StaminaData.ActiveModifier active : StaminaData.of(player).getActiveModifiers()) {
            list.add(new ModifierEntry(active.modifier, active.remainingTicks));
        }
        return list;
    }

    /** 修改器条目（修改器 + 剩余 tick）。 */
    public record ModifierEntry(StaminaModifier modifier, int remainingTicks) {
    }

    // ------------------------------------------------------------------ 读取

    public static float getStamina(Player player) {
        if (useClientDisplay(player)) {
            return clientDisplay.max() * (float) clientDisplay.ratio();
        }
        return StaminaData.of(player).getStamina();
    }

    public static float getMaxStamina(Player player) {
        if (useClientDisplay(player)) {
            return clientDisplay.max();
        }
        return StaminaData.of(player).getMaxStamina();
    }

    /** 当前体力比例 [0, 1]。 */
    public static float getRatio(Player player) {
        if (useClientDisplay(player)) {
            return (float) clientDisplay.ratio();
        }
        return StaminaData.of(player).getRatio();
    }

    /** 是否处于透支锁定（归零后，恢复到阈值前）。 */
    public static boolean isExhausted(Player player) {
        if (useClientDisplay(player)) {
            return clientDisplay.depleted();
        }
        return StaminaData.of(player).isExhaustedLock();
    }

    /**
     * 低体力惩罚判定（预留接口）：默认 = 未透支 且 高于疾跑停止阈值。
     * 生态模组可以叠加自己的条件（装备重量、骨折状态……），
     * 不需要本模组知道任何细节。
     */
    public static boolean canSprint(Player player) {
        return !StaminaData.of(player).isSprintBlocked();
    }

    // ------------------------------------------------------------------ 写入（仅服务端）

    /**
     * 原子扣除：体力不足则完全不扣并返回 false。
     * 触发可取消/可改量的 {@link com.herra.stamina.api.event.StaminaEvent.Consume}。
     */
    public static boolean tryConsume(Player player, float amount, StaminaAction action) {
        requireServer(player, "tryConsume");
        if (StaminaData.of(player).getStamina() < amount) {
            return false;
        }
        return StaminaManager.drain(player, amount, action, false) >= amount - 0.001F;
    }

    /** 部分扣除：有多少扣多少，返回实际扣除量。 */
    public static float drain(Player player, float amount, StaminaAction action) {
        requireServer(player, "drain");
        return StaminaManager.drain(player, amount, action, false);
    }

    /** 直接增加体力（钳制到上限）。 */
    public static void addStamina(Player player, float amount) {
        requireServer(player, "addStamina");
        StaminaManager.setStamina(player, StaminaData.of(player).getStamina() + amount);
    }

    /** 直接设置体力（钳制 + 事件 + 立即同步）。 */
    public static void setStamina(Player player, float amount) {
        requireServer(player, "setStamina");
        StaminaManager.setStamina(player, amount);
    }

    /** 重置为满体力（新战局开始时战局系统可调用）。 */
    public static void reset(Player player) {
        requireServer(player, "reset");
        StaminaManager.setStamina(player, StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA));
    }

    // ------------------------------------------------------------------ 内部

    private static boolean useClientDisplay(Player player) {
        return player.level().isClientSide && player.isLocalPlayer() && clientDisplay != null;
    }

    private static void requireServer(Player player, String method) {
        if (player.level().isClientSide) {
            throw new IllegalStateException("StaminaAPI." + method + " 仅服务端调用");
        }
    }

    private StaminaAPI() {
    }
}
