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
 * @param questTitle the title of the quest that was started
 */
public record ClientboundQuestStartedPayload(Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestStartedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_started"));

    public static final StreamCodec<ByteBuf, ClientboundQuestStartedPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).map(ClientboundQuestStartedPayload::new, ClientboundQuestStartedPayload::questTitle);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestStartedPayload> type() {
        return TYPE;
    }
}
