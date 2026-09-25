package com.herra.stamina.core;

import com.herra.stamina.HerraStamina;
import com.herra.stamina.api.StaminaAction;
import com.herra.stamina.api.StaminaAPI;
import com.herra.stamina.api.event.StaminaEvent;
import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        StaminaData st = StaminaData.of(player);

        // ---- 修改器倒计时（到期自动失效，上限可能变化） ----
        st.tickModifiers();

        // ---- 消耗来源判定 ----
        // 注意：移动检测必须用位置差（StaminaData.updateMovement）。
        // 服务端玩家的 deltaMovement 不随输入更新（Entity.move 只写坐标），
        // 用它判断“是否在移动”会恒为 false —— 这是 v1.0.0 疾跑不消耗的原因。
        boolean moving = st.updateMovement(player);
        boolean creative = player.isCreative() || player.isSpectator();
        if (!creative || StaminaServerConfig.APPLY_IN_CREATIVE.get()) {
            tickRules(player, st, moving);
        }

        // 透支惩罚（跳跃禁止属性修饰符）必须在创造/旁观分支之外处理：
        // 创造模式下锁定不可能为 true，此调用负责摘除切模式前残留的修饰符。
        updateExhaustionEffects(player, st, creative);

        maybeSync(player, st, false);
    }

    /** 体力规则主体（消耗 / 恢复 / 禁跑兜底）。 */
    private static void tickRules(ServerPlayer player, StaminaData st, boolean moving) {
        float drain = 0.0F;
        StaminaAction action = StaminaAction.SPRINT;
        if (player.isInWater() && player.isSwimming()
                && (moving || st.movedVertically())) {
            // 游泳（爬泳姿势）：水平或垂直主动位移都算消耗（下潜/上浮）；
            // 垂直分量用较大阈值排除水中被动下沉。
            action = player.isSprinting()
                    ? StaminaAction.SWIM_SPRINT : StaminaAction.SWIM;
            drain = action == StaminaAction.SWIM_SPRINT
                    ? StaminaServerConfig.f(StaminaServerConfig.SWIM_SPRINT_DRAIN_PER_SECOND)
                    : StaminaServerConfig.f(StaminaServerConfig.SWIM_DRAIN_PER_SECOND);
        } else if (player.isSprinting()
                && !player.getAbilities().flying
                && !player.isFallFlying()   // 滑翔不是疾跑
                && !player.isPassenger()    // 骑乘时是坐骑在跑，骑手不消耗
                && moving) {
            action = StaminaAction.SPRINT;
            drain = StaminaServerConfig.f(StaminaServerConfig.SPRINT_DRAIN_PER_SECOND);
        }

        if (drain > 0.0F) {
            // 持续消耗：全局消耗修改器与修改器倍率统一在 drain() 内应用
            // （不逐 tick 触发 Consume 事件，避免事件风暴）
            drain(player, drain / 20.0F, action, true);
            st.setTicksSinceConsumption(0);
        } else {
            recover(player, st);
            st.setTicksSinceConsumption(st.getTicksSinceConsumption() + 1);
        }

        // ---- 低体力禁跑（服务端权威兜底；客户端由 LocalPlayerMixin 在
        // 原版饥饿禁跑的同一判定点镜像压制，避免疾跑 FOV 抖动） ----
        if (player.isSprinting() && !StaminaAPI.canSprint(player)) {
            player.setSprinting(false);
        }
    }

    // ------------------------------------------------------------------ 透支惩罚

    /**
     * 透支锁定期禁止跳跃：给 JUMP_STRENGTH 挂负向修饰符（钳制到 0）。
     *
     * <p>JUMP_STRENGTH 是同步属性（骑马跳跃即依赖此机制），修饰符会自动
     * 同步到客户端 —— 客户端 jumpFromGround() 的 {@code f <= 1.0E-5} 守卫
     * 直接跳过，两端一致、无橡皮筋。跳跃药水在透支期间仍会带来 0.1/级
     * 的微小弹跳（原版公式加算），可接受。</p>
     *
     * <p>幂等：每 tick 调用，状态与修饰符对齐即可；登录/重生/换维度后
     * 新实体无修饰符，锁定状态也不会跨体保留，自愈。</p>
     */
    private static void updateExhaustionEffects(ServerPlayer player, StaminaData st, boolean creative) {
        boolean blockJump = st.isExhaustedLock()
                && StaminaServerConfig.EXHAUSTED_BLOCK_JUMP.get()
                && (!creative || StaminaServerConfig.APPLY_IN_CREATIVE.get());
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null) {
            return; // 理论上玩家实体必定有该属性（LivingEntity 基础属性表）
        }
        boolean present = jump.hasModifier(JUMP_BLOCK_ID);
        if (blockJump && !present) {
            jump.addTransientModifier(JUMP_BLOCK_MODIFIER);
        } else if (!blockJump && present) {
            jump.removeModifier(JUMP_BLOCK_ID);
        }
    }

    private static final ResourceLocation JUMP_BLOCK_ID = HerraStamina.id("exhausted_jump_block");
    private static final AttributeModifier JUMP_BLOCK_MODIFIER = new AttributeModifier(
            JUMP_BLOCK_ID, -1024.0, AttributeModifier.Operation.ADD_VALUE);

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
        if (st.getTicksSinceConsumption() < delay) {
            return;
        }
        float rate = lock
                ? StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RECOVERY_PER_SECOND)
                : StaminaServerConfig.f(StaminaServerConfig.RECOVERY_PER_SECOND);
        // 修改器：乘算 × 加算（正常与透支恢复都生效）
        rate = rate * st.getRegenMultiplier() + st.getRegenBonus();
        if (rate <= 0.0F) {
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
     * @param continuous true = 疾跑/游泳等持续消耗（跳过 Consume 事件，应用消耗修改器）
     */
    public static float drain(Player player, float amount, StaminaAction action, boolean continuous) {
        StaminaData st = StaminaData.of(player);
        float current = st.getStamina();
        if (amount <= 0.0F || current <= 0.0F) {
            return 0.0F;
        }

        if (continuous) {
            // 全局消耗规则修改器（StaminaDrainModifier）× 修改器倍率（医药 buff 等）
            amount = StaminaAPI.applyDrainModifiers(player, action, amount) * st.getDrainMultiplier();
            if (amount <= 0.0F) {
                return 0.0F;
            }
        } else {
            StaminaEvent.Consume event = new StaminaEvent.Consume(player, action, amount);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled() || event.getAmount() <= 0.0F) {
                return 0.0F;
            }
            // 一次性消耗同样吃修改器倍率（药品“消耗减半”应覆盖跳跃等全部动作）
            amount = event.getAmount() * st.getDrainMultiplier();
            if (amount <= 0.0F) {
                return 0.0F;
            }
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
     * 代价是直接进入透支锁定。透支锁定期间跳跃被 JUMP_STRENGTH 修饰符
     * 整体禁止（见 updateExhaustionEffects），事件不会再触发。 */
    public static void handleJump(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float base = StaminaServerConfig.f(StaminaServerConfig.JUMP_COST);
        // 全局消耗规则修改器（重甲加重等）；修改器倍率与 Consume 事件在 drain() 内处理
        float cost = StaminaAPI.applyDrainModifiers(player, StaminaAction.JUMP, base);
        if (cost <= 0.0F) {
            return;
        }
        drain(player, cost, StaminaAction.JUMP, false);
    }

    /** 近战攻击命中实体的一次性消耗（AttackEntityEvent，仅服务端结算）。 */
    public static void handleAttack(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float base = StaminaServerConfig.f(StaminaServerConfig.ATTACK_COST);
        if (base <= 0.0F) {
            return; // 配置关闭
        }
        float cost = StaminaAPI.applyDrainModifiers(player, StaminaAction.ATTACK, base);
        if (cost <= 0.0F) {
            return;
        }
        drain(player, cost, StaminaAction.ATTACK, false);
    }

    /** 破坏方块的一次性消耗（BlockEvent.BreakEvent，仅服务端触发）。 */
    public static void handleBreakBlock(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float base = StaminaServerConfig.f(StaminaServerConfig.BREAK_BLOCK_COST);
        if (base <= 0.0F) {
            return; // 配置关闭
        }
        float cost = StaminaAPI.applyDrainModifiers(player, StaminaAction.BREAK_BLOCK, base);
        if (cost <= 0.0F) {
            return;
        }
        drain(player, cost, StaminaAction.BREAK_BLOCK, false);
    }

    // ------------------------------------------------------------------ 直接设置（API / 指令用）

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
