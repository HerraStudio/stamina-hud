package com.herra.stamina.client;

import com.herra.stamina.network.StaminaSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端包处理器（仅在客户端加载）。
 */
public final class ClientPacketHandlers {

    public static void handleStaminaSync(StaminaSyncPayload payload, IPayloadContext context) {
        // 切回主线程更新动画状态（play 通道默认主线程，enqueueWork 保证万无一失）
        context.enqueueWork(() -> ClientStaminaData.acceptSync(payload));
    }

    private ClientPacketHandlers() {
    }
}
