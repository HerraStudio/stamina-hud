package com.herra.stamina.client;

import com.herra.stamina.client.gui.StaminaSettingsScreen;
import net.minecraft.client.Minecraft;

/**
 * client-only GUI 入口中转（服务器永不加载本类）。
 *
 * <p>ClientPacketHandlers 的方法体只能<b>间接</b>引用本类：
 * 直接在可被服务端加载的类的方法体里 new Screen / 引用 Minecraft
 * 会在专用服务器的 dist 检查中崩溃（BootstrapMethodError: invalid dist）。</p>
 */
public final class ClientGuiHandlers {

    public static void openSettings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new StaminaSettingsScreen());
        }
    }

    private ClientGuiHandlers() {
    }
}
