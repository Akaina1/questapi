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
 * Sent to a client when a chapter final quest is ready to be turned in, purely to drive the
 * "last chance" toast and sound: handing the quest in ends the chapter and fails what is left in it.
 *
 * @param title   the toast's title line
 * @param message the toast's body, already resolved on the server (chapter number and finale quest)
 */
public record ClientboundChapterEndingPayload(Component title, Component message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundChapterEndingPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "chapter_ending"));

    private static final StreamCodec<ByteBuf, Component> COMPONENT = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC);

    public static final StreamCodec<ByteBuf, ClientboundChapterEndingPayload> STREAM_CODEC = StreamCodec.composite(
            COMPONENT, ClientboundChapterEndingPayload::title,
            COMPONENT, ClientboundChapterEndingPayload::message,
            ClientboundChapterEndingPayload::new);

    @Override
    public CustomPacketPayload.Type<ClientboundChapterEndingPayload> type() {
        return TYPE;
    }
}
