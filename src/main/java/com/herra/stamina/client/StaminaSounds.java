package com.herra.stamina.client;

import com.herra.stamina.HerraStamina;
import com.herra.stamina.config.StaminaClientConfig;
import com.herra.stamina.core.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端音效播放器：体力耗尽提示音（可调音量/关闭）。
 *
 * <p>用 forUI 实例播放（走主声道，不受 3D 衰减影响），音量映射
 * {@code sound.exhausted_volume 0~100 -> 0.0~1.25}，设 0 或关闭开关即静音。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class StaminaSounds {

    /** 播放体力耗尽提示音（音量走客户端配置）。 */
    public static void playExhausted() {
        if (!StaminaClientConfig.SOUND_ENABLED.get()) {
            return;
        }
        int vol = StaminaClientConfig.SOUND_VOLUME.get();
        if (vol <= 0) {
            return;
        }
        playExhausted(Mth.clamp(vol / 100.0F, 0.0F, 1.0F) * 1.25F);
    }

    /** 按指定音量播放（设置界面「试听」按钮用，忽略开关但尊重音量）。 */
    public static void playExhausted(float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                ModSounds.EXHAUSTED.get(), 1.0F, Mth.clamp(volume, 0.0F, 4.0F)));
    }

    private StaminaSounds() {
    }
}
