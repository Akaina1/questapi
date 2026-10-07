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
 * Sent to a client when one of their quests fails, purely to drive a "quest failed" toast and sound.
 *
 * @param toastTitle the toast's title line, already resolved on the server (the quest's
 *                   {@code toast_overrides.failed} or the default)
 * @param questTitle the title of the quest that failed
 */
public record ClientboundQuestFailedPayload(Component toastTitle, Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestFailedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_failed"));

    private static final StreamCodec<ByteBuf, Component> COMPONENT = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC);

    public static final StreamCodec<ByteBuf, ClientboundQuestFailedPayload> STREAM_CODEC = StreamCodec.composite(
            COMPONENT, ClientboundQuestFailedPayload::toastTitle,
            COMPONENT, ClientboundQuestFailedPayload::questTitle,
            ClientboundQuestFailedPayload::new);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestFailedPayload> type() {
        return TYPE;
    }
}
