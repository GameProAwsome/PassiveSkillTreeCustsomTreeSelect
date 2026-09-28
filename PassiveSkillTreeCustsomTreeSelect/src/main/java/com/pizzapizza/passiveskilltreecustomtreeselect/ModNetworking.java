package com.pizzapizza.passiveskilltreecustomtreeselect;

import com.pizzapizza.passiveskilltreecustomtreeselect.AdvancementNameCache.AdvancementNameResponsePayload;
import com.pizzapizza.passiveskilltreecustomtreeselect.AdvancementNameCache.RequestAdvancementNamePayload;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "passive_skill_tree_custom_tree_select")
public class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(AdvancementNameCache.RequestAdvancementNamePayload.TYPE, RequestAdvancementNamePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        var holder = serverPlayer.getServer().getAdvancements().get(payload.advancementId());
                        String name = holder != null
                                ? Advancement.name(holder).getString()
                                : payload.advancementId().toString();
                        PacketDistributor.sendToPlayer(serverPlayer,
                                new AdvancementNameResponsePayload(payload.advancementId(), name));
                    }
                }));

        registrar.playToClient(AdvancementNameResponsePayload.TYPE, AdvancementNameResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        AdvancementNameCache.put(payload.advancementId(), payload.name())));
    }
}