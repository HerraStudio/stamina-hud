package com.herra.stamina.core;

import com.herra.stamina.HerraStamina;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registry of runtime data attachments. Deliberately NOT persistent:
 * stamina resets to full on login/respawn, which is the desired
 * raid-based behavior for extraction-shooter gameplay.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HerraStamina.MOD_ID);

    public static final Supplier<AttachmentType<StaminaData>> STAMINA =
            ATTACHMENT_TYPES.register("stamina",
                    () -> AttachmentType.builder(StaminaData::new).build());

    private ModAttachments() {
    }
}
