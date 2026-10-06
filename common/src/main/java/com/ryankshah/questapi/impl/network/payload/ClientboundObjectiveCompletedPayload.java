package com.ryankshah.questapi.impl.network.payload;

import com.ryankshah.questapi.QuestApi;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent to a client when one objective of their quest reaches its target, purely to drive an
 * "objective complete" toast and sound. Only fires on completion, never for partial progress.
 *
 * @param objectiveDescription the human-readable description of the finished objective
 */
public record ClientboundObjectiveCompletedPayload(Component objectiveDescription) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundObjectiveCompletedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "objective_completed"));

    public static final StreamCodec<ByteBuf, ClientboundObjectiveCompletedPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).map(ClientboundObjectiveCompletedPayload::new, ClientboundObjectiveCompletedPayload::objectiveDescription);

    @Override
    public CustomPacketPayload.Type<ClientboundObjectiveCompletedPayload> type() {
        return TYPE;
    }
}
