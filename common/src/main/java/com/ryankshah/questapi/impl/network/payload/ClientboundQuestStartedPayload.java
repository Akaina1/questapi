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
 * Sent to a client when one of their quests becomes {@code ACTIVE} (accepted through the quest book,
 * an NPC dialog or {@code autoActivate}), purely to drive a "quest accepted" toast and sound.
 *
 * @param toastTitle the toast's title line, already resolved on the server (the quest's
 *                   {@code toast_overrides.started} or the default)
 * @param questTitle the title of the quest that was started
 */
public record ClientboundQuestStartedPayload(Component toastTitle, Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestStartedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_started"));

    private static final StreamCodec<ByteBuf, Component> COMPONENT = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC);

    public static final StreamCodec<ByteBuf, ClientboundQuestStartedPayload> STREAM_CODEC = StreamCodec.composite(
            COMPONENT, ClientboundQuestStartedPayload::toastTitle,
            COMPONENT, ClientboundQuestStartedPayload::questTitle,
            ClientboundQuestStartedPayload::new);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestStartedPayload> type() {
        return TYPE;
    }
}
