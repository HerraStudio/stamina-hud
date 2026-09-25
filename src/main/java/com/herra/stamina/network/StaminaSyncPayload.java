package com.herra.stamina.network;

import com.herra.stamina.HerraStamina;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端 -> 客户端体力同步包（仅发给体力所属玩家本人）。
 *
 * @param stamina        当前体力值
 * @param maxStamina     最大体力值（配置可能随时变化，随包携带）
 * @param exhaustedLock  是否处于透支锁定（归零后需恢复到阈值才能再跑）
 * @param sprintBlocked  当前是否禁止疾跑（低于阈值或透支）
 */
public record StaminaSyncPayload(
        float stamina,
        float maxStamina,
        boolean exhaustedLock,
        boolean sprintBlocked
) implements CustomPacketPayload {

    public static final Type<StaminaSyncPayload> TYPE = new Type<>(HerraStamina.id("stamina_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StaminaSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaSyncPayload::stamina,
            ByteBufCodecs.FLOAT, StaminaSyncPayload::maxStamina,
            ByteBufCodecs.BOOL, StaminaSyncPayload::exhaustedLock,
            ByteBufCodecs.BOOL, StaminaSyncPayload::sprintBlocked,
            StaminaSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, float stamina, float maxStamina,
                              boolean exhaustedLock, boolean sprintBlocked) {
        PacketDistributor.sendToPlayer(player,
                new StaminaSyncPayload(stamina, maxStamina, exhaustedLock, sprintBlocked));
    }
}
