package com.herra.stamina.mixin;

import com.herra.stamina.client.ClientStaminaData;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 低体力禁跑 —— 与原版饥饿禁跑共用同一判定点。
 *
 * <p>原版在 {@code LocalPlayer.hasEnoughFoodToStartSprinting()} 里同时门控了
 * 疾跑的「启动」（canStartSprinting / keySprint 分支）与「维持」
 * （aiStep 的停止条件），因此饥饿禁跑平滑无抖动。体力条复制这一机制：
 * 服务端把 {@code sprintBlocked} 标志随同步包下发，客户端在本判定点返回
 * false，即可覆盖按住疾跑键、切换式疾跑（toggle sprint）、双击 W 三种启动
 * 方式，且 FOV 平滑回落、无每 tick 重启抖动。</p>
 *
 * <p>服务端仍有 {@code setSprinting(false)} 权威兜底（见 StaminaManager），
 * 本 mixin 只负责客户端体验与客户端权威移动的速度压制。</p>
 *
 * <p>注意：骑乘状态原版该方法恒为 true（坐骑不需要食物）。透支骑手同样
 * 被禁止疾跑（坐骑疾驰）——与「体力透支 = 不能疾跑」的统一规则一致。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void herraStamina$blockSprintWhenWinded(CallbackInfoReturnable<Boolean> cir) {
        if (ClientStaminaData.isSprintBlocked()) {
            cir.setReturnValue(false);
        }
    }
}
