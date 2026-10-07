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
 * Sent to a client the moment one of their quests becomes {@code COMPLETED}, purely to drive the
 * completion sound and toast - separate from {@link ClientboundSyncProgressPayload} so the GUI's
 * progress cache and the one-shot feedback effect stay independent concerns.
 *
 * @param toastTitle the toast's title line, already resolved on the server (the quest's
 *                   {@code toast_overrides.ready} or the default)
 * @param questTitle the title of the quest that is ready to turn in
 */
public record ClientboundQuestCompletedPayload(Component toastTitle, Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestCompletedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_completed"));

    private static final StreamCodec<ByteBuf, Component> COMPONENT = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC);

    public static final StreamCodec<ByteBuf, ClientboundQuestCompletedPayload> STREAM_CODEC = StreamCodec.composite(
            COMPONENT, ClientboundQuestCompletedPayload::toastTitle,
            COMPONENT, ClientboundQuestCompletedPayload::questTitle,
            ClientboundQuestCompletedPayload::new);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestCompletedPayload> type() {
        return TYPE;
    }
}
