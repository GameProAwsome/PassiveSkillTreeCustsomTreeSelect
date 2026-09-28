package com.pizzapizza.passiveskilltreecustomtreeselect;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancementNameCache {
    private static final Map<ResourceLocation, String> CACHE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> PENDING = ConcurrentHashMap.newKeySet();

    /** Returns the cached name if known, otherwise fires off a request and returns the raw id in the meantime. */
    public static String get(ResourceLocation advancementId) {
        String cached = CACHE.get(advancementId);
        if (cached != null) return cached;

        if (PENDING.add(advancementId)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RequestAdvancementNamePayload(advancementId));
        }
        return advancementId.toString();
    }

    public static void put(ResourceLocation advancementId, String name) {
        CACHE.put(advancementId, name);
        PENDING.remove(advancementId);
    }

    public record RequestAdvancementNamePayload(ResourceLocation advancementId) implements CustomPacketPayload {
        public static final Type<RequestAdvancementNamePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("passive_skill_tree_custom_tree_select", "request_advancement_name"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestAdvancementNamePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, RequestAdvancementNamePayload::advancementId,
                        RequestAdvancementNamePayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AdvancementNameResponsePayload(ResourceLocation advancementId, String name) implements CustomPacketPayload {
        public static final Type<AdvancementNameResponsePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("passive_skill_tree_custom_tree_select", "advancement_name_response"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementNameResponsePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, AdvancementNameResponsePayload::advancementId,
                        ByteBufCodecs.STRING_UTF8, AdvancementNameResponsePayload::name,
                        AdvancementNameResponsePayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}