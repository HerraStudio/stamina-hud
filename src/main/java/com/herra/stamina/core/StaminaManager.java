package com.herra.stamina.core;

import com.herra.stamina.api.StaminaAction;
import com.herra.stamina.api.StaminaAPI;
import com.herra.stamina.api.event.StaminaEvent;
import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 全部服务端体力规则。由 {@link StaminaGameEvents} 每玩家 tick 驱动，
 * 公开 API（StaminaAPI）的自定义消耗也从这里进入，保证事件与同步一致。
 */
public final class StaminaManager {

    // ------------------------------------------------------------------ tick

    /** 每服务端 tick 对每个玩家调用一次。 */
    public static void tickPlayer(ServerPlayer player) {
        boolean creative = player.isCreative() || player.isSpectator();
        if (creative && !StaminaServerConfig.APPLY_IN_CREATIVE.get()) {
            return; // 创造模式不消耗、不恢复（HUD 客户端自动隐藏）
        }

        StaminaData st = StaminaData.of(player);

        // ---- 消耗来源判定 ----
        float drain = 0.0F;
        StaminaAction action = StaminaAction.SPRINT;
        if (player.isInWater() && player.isSwimming()) {
            action = player.isSprinting()
                    ? StaminaAction.SWIM_SPRINT : StaminaAction.SWIM;
            drain = action == StaminaAction.SWIM_SPRINT
                    ? StaminaServerConfig.f(StaminaServerConfig.SWIM_SPRINT_DRAIN_PER_SECOND)
                    : StaminaServerConfig.f(StaminaServerConfig.SWIM_DRAIN_PER_SECOND);
        } else if (player.isSprinting()
                && !player.getAbilities().flying
                && player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) {
            action = StaminaAction.SPRINT;
            drain = StaminaServerConfig.f(StaminaServerConfig.SPRINT_DRAIN_PER_SECOND);
        }

        if (drain > 0.0F) {
            // 持续消耗走 drain modifier（不逐 tick 触发 Consume 事件，避免事件风暴）
            drain = StaminaAPI.applyDrainModifiers(player, action, drain) / 20.0F;
            drain(player, drain, action, true);
            st.setTicksSinceConsumption(0);
        } else {
            recover(player, st);
            st.setTicksSinceConsumption(st.getTicksSinceConsumption() + 1);
        }

        // ---- 低体力禁跑（服务端权威压制；客户端按同步标志镜像，避免疾跑 FOV 抖动） ----
        if (player.isSprinting() && !StaminaAPI.canSprint(player)) {
            player.setSprinting(false);
        }

        maybeSync(player, st, false);
    }

    // ------------------------------------------------------------------ 恢复

    private static void recover(ServerPlayer player, StaminaData st) {
        float max = st.getMaxStamina();
        float current = st.getStamina();
        if (current >= max) {
            return;
        }
        boolean lock = st.isExhaustedLock();
        int delay = StaminaServerConfig.RECOVERY_DELAY_TICKS.get()
                + (lock ? StaminaServerConfig.EXHAUSTED_RECOVERY_DELAY_TICKS.get() : 0);
        float rate = lock
                ? StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RECOVERY_PER_SECOND)
                : StaminaServerConfig.f(StaminaServerConfig.RECOVERY_PER_SECOND);
        if (rate <= 0.0F || st.getTicksSinceConsumption() < delay) {
            return;
        }
        float now = Math.min(max, current + rate / 20.0F);
        st.setStaminaRaw(now);
        NeoForge.EVENT_BUS.post(new StaminaEvent.Changed(player, current, now));
        if (lock && now >= StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RELEASE_THRESHOLD)) {
            st.setExhaustedLock(false);
            NeoForge.EVENT_BUS.post(new StaminaEvent.Recovered(player));
        }
    }

    // ------------------------------------------------------------------ 消耗

    /**
     * 部分扣除：有多少扣多少，返回实际扣除量。
     *
     * @param continuous true = 疾跑/游泳等持续消耗（跳过 Consume 事件）
     */
    public static float drain(Player player, float amount, StaminaAction action, boolean continuous) {
        StaminaData st = StaminaData.of(player);
        float current = st.getStamina();
        if (amount <= 0.0F || current <= 0.0F) {
            return 0.0F;
        }

        if (continuous) {
            amount = StaminaAPI.applyDrainModifiers(player, action, amount);
            if (amount <= 0.0F) {
                return 0.0F;
            }
        } else {
            StaminaEvent.Consume event = new StaminaEvent.Consume(player, action, amount);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled() || event.getAmount() <= 0.0F) {
                return 0.0F;
            }
            amount = event.getAmount();
        }

        float cost = Math.min(amount, current);
        float now = current - cost;
        st.setStaminaRaw(now);
        st.setTicksSinceConsumption(0);

        if (now <= 0.0001F && !st.isExhaustedLock()) {
            st.setExhaustedLock(true);
            NeoForge.EVENT_BUS.post(new StaminaEvent.Exhausted(player));
        }
        NeoForge.EVENT_BUS.post(new StaminaEvent.Changed(player, current, now));
        return cost;
    }

    /** 跳跃消耗：NeoForge 1.21.1 的 LivingJumpEvent 不可取消， 因此策略为
     * 「允许跳跃、一次性扣到空」——战术上允许最后一丝体力赌命跳，
     * 代价是直接进入透支锁定。 */
    public static void handleJump(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float base = StaminaServerConfig.f(StaminaServerConfig.JUMP_COST);
        float cost = StaminaAPI.applyDrainModifiers(player, StaminaAction.JUMP, base);
        if (cost <= 0.0F) {
            return;
        }
        drain(player, cost, StaminaAction.JUMP, false);
    }

    // ------------------------------------------------------------------ 直接设置（API 用）

    public static void setStamina(Player player, float value) {
        StaminaData st = StaminaData.of(player);
        float old = st.getStamina();
        float now = Math.max(0.0F, Math.min(st.getMaxStamina(), value));
        st.setStaminaRaw(now);
        st.setTicksSinceConsumption(0);

        if (now <= 0.0001F && !st.isExhaustedLock()) {
            st.setExhaustedLock(true);
            NeoForge.EVENT_BUS.post(new StaminaEvent.Exhausted(player));
        } else if (now >= StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RELEASE_THRESHOLD)
                && st.isExhaustedLock()) {
            st.setExhaustedLock(false);
            NeoForge.EVENT_BUS.post(new StaminaEvent.Recovered(player));
        }
        if (old != now) {
            NeoForge.EVENT_BUS.post(new StaminaEvent.Changed(player, old, now));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            syncNow(serverPlayer);
        }
    }

    // ------------------------------------------------------------------ 同步

    /** 登录/重生/API 修改后立即同步。 */
    public static void syncNow(ServerPlayer player) {
        StaminaData st = StaminaData.of(player);
        if (st.getStamina() < 0.0F) {
            st.setStaminaRaw(st.getMaxStamina());
        }
        st.sendTo(player);
        st.setSyncCooldown(StaminaServerConfig.SYNC_INTERVAL_TICKS.get());
    }

    private static void maybeSync(ServerPlayer player, StaminaData st, boolean force) {
        st.tickCooldown();
        if ((force || st.shouldSync()) && st.cooldownReady()) {
            st.sendTo(player);
            st.setSyncCooldown(StaminaServerConfig.SYNC_INTERVAL_TICKS.get());
        }
    }

    private StaminaManager() {
    }
}
