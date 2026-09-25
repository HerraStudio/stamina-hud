package com.herra.stamina.network;

import com.herra.stamina.client.ClientPacketHandlers;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络注册。ClientPacketHandlers 仅在客户端被调用/加载
 * （NeoForge S2C 处理器的标准模式）。
 */
public final class ModNetworking {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                StaminaSyncPayload.TYPE,
                StaminaSyncPayload.STREAM_CODEC,
                ClientPacketHandlers::handleStaminaSync);
        registrar.playToClient(
                OpenSettingsPayload.TYPE,
                OpenSettingsPayload.STREAM_CODEC,
                ClientPacketHandlers::handleOpenSettings);
    }

    private ModNetworking() {
    }
}
