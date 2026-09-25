package com.herra.stamina.core;

import com.herra.stamina.HerraStamina;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组音效注册。目前仅一枚：体力透支提示音（低频心跳，客户端播放）。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, HerraStamina.MOD_ID);

    /** 体力耗尽瞬间的心跳闷响（assets/herra_stamina/sounds/exhausted.ogg）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXHAUSTED =
            SOUND_EVENTS.register("exhausted",
                    () -> SoundEvent.createVariableRangeEvent(HerraStamina.id("exhausted")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }

    private ModSounds() {
    }
}
