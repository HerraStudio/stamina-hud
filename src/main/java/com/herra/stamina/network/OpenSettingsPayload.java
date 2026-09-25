package com.herra.stamina.network;

import com.herra.stamina.HerraStamina;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端 -> 客户端：请求打开体力设置界面（{@code /sta} 指令触发）。
 *
 * <p>空载荷 —— 只是让客户端弹 {@code StaminaSettingsScreen}。
 * 界面内的全部调整都作用于客户端本地配置（每玩家各自生效），
 * 不回传服务器。</p>
 */
public record OpenSettingsPayload() implements CustomPacketPayload {

    public static final Type<OpenSettingsPayload> TYPE = new Type<>(HerraStamina.id("open_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSettingsPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenSettingsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenSettingsPayload());
    }
}
