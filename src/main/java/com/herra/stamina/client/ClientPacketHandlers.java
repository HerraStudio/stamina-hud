package com.herra.stamina.client;

import com.herra.stamina.network.OpenSettingsPayload;
import com.herra.stamina.network.StaminaSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端包处理器（仅在客户端被调用/加载）。
 *
 * <p>注意：本类会被专用服务器在 payload 注册时加载（方法引用触发），
 * 方法体内只能<b>间接</b>引用 client-only 类型（通过 ClientStaminaData /
 * ClientGuiHandlers 等中转类），直接引用 Minecraft / Screen 会在
 * 服务器的 dist 检查中崩溃。</p>
 */
public final class ClientPacketHandlers {

    public static void handleStaminaSync(StaminaSyncPayload payload, IPayloadContext context) {
        // 切回主线程更新动画状态（play 通道默认主线程，enqueueWork 保证万无一失）
        context.enqueueWork(() -> ClientStaminaData.acceptSync(payload));
    }

    public static void handleOpenSettings(OpenSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(ClientGuiHandlers::openSettings);
    }

    private ClientPacketHandlers() {
    }
}
