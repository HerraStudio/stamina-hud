package com.herra.stamina.client;

import com.herra.stamina.HerraStamina;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端游戏总线事件：动画簿记 + 低体力疾跑镜像压制。
 */
@EventBusSubscriber(modid = HerraStamina.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientStaminaData.clientTick(Minecraft.getInstance());
    }

    private ClientEvents() {
    }
}
