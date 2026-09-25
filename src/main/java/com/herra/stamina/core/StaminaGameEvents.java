package com.herra.stamina.core;

import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 原版/NeoForge 游戏事件 -> 体力规则 的绑定。游戏总线注册。
 */
public final class StaminaGameEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.tickPlayer(serverPlayer);
        }
    }

    /**
     * 跳跃消耗。NeoForge 21.1 的 LivingJumpEvent 在 jumpFromGround() 内
     * 触发（含跳跃药水/马匹/骆驼等变体），不可取消 —— 策略是允许跳跃、
     * 一次性扣费（体力不足则扣到空并进入透支锁定）。透支期间跳跃被
     * JUMP_STRENGTH 修饰符整体禁止，不会走到这里。
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.handleJump(serverPlayer);
        }
    }

    /**
     * 近战攻击消耗。事件在攻击目标结算前触发（双端），只处理服务端：
     * 服务端的 Player.attack 一定会走到（客户端只是预表现）。
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.handleAttack(serverPlayer);
        }
    }

    /**
     * 破坏方块消耗（仅在服务端 ServerPlayerGameMode.destroyBlock 中触发）。
     */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            StaminaManager.handleBreakBlock(serverPlayer);
        }
    }

    /** 新战局/首次进入：满体力开局。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.setStamina(serverPlayer, StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA));
        }
    }

    /** 重生 = 新身体，重置为满体力并立即同步。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.setStamina(serverPlayer, StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA));
        }
    }

    /** 跨维度后客户端 HUD 立即刷新。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StaminaManager.syncNow(serverPlayer);
        }
    }

    private StaminaGameEvents() {
    }
}
