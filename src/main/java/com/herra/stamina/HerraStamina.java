package com.herra.stamina;

import com.herra.stamina.config.StaminaClientConfig;
import com.herra.stamina.config.StaminaServerConfig;
import com.herra.stamina.core.ModAttachments;
import com.herra.stamina.core.StaminaCommands;
import com.herra.stamina.core.StaminaGameEvents;
import com.herra.stamina.network.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HERRA Stamina —— 搜打撤服务器的独立体力系统（NeoForge 1.21.1）。
 *
 * <p>单一职责：只做体力条。不与 GWO 枪械、淘汰播报、战局系统硬耦合；
 * 生态模组通过 {@code com.herra.stamina.api}（StaminaAPI / 事件 /
 * 消耗规则修改器）单向集成，可独立启用/禁用。</p>
 *
 * <p>服务端权威：体力数值与规则全部在服务端 tick 计算（Attachment 存储），
 * 客户端只渲染 LDLib2 ModularUI 驱动的像素风 HUD。</p>
 */
@Mod(HerraStamina.MOD_ID)
public class HerraStamina {

    public static final String MOD_ID = "herra_stamina";
    public static final Logger LOGGER = LoggerFactory.getLogger("HERRAStamina");

    public HerraStamina(IEventBus modBus, ModContainer container) {
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(ModNetworking::registerPayloads);

        container.registerConfig(ModConfig.Type.SERVER, StaminaServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, StaminaClientConfig.SPEC);

        // 服务端玩法逻辑 + 游戏内调节指令（游戏总线）
        NeoForge.EVENT_BUS.register(StaminaGameEvents.class);
        NeoForge.EVENT_BUS.register(StaminaCommands.class);

        LOGGER.info("HERRA Stamina 已加载，体力系统就绪。");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
