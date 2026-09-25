package com.herra.stamina.client;

import com.google.common.base.Suppliers;
import com.herra.stamina.HerraStamina;
import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.herra.stamina.client.hud.StaminaHudElement;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.function.Supplier;

/**
 * HUD 层注册（LDLib2 ModularHudLayer，见官方文档 HUD Overlays 章节）。
 *
 * <p>按文档要求用 memoize 延迟构建 —— 注册事件触发时资源与世界尚未就绪，
 * ModularUI 必须留到第一次渲染时再创建。</p>
 */
@EventBusSubscriber(modid = HerraStamina.MOD_ID, value = Dist.CLIENT)
public final class HerraStaminaClient {

    private static final Supplier<ModularUI> HUD_UI = Suppliers.memoize(() ->
            ModularUI.of(UI.of(new StaminaHudElement())));

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 挂在所有原版 HUD 之上，自身为全屏透明层，内部自行定位到物品栏上方
        event.registerAboveAll(HerraStamina.id("stamina_hud"), (ModularHudLayer) () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.player != null && mc.level != null ? HUD_UI.get() : null;
        });
    }

    private HerraStaminaClient() {
    }
}
