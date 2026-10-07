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
 * Sent to a client when their quest's rewards are claimed (the quest becomes {@code REWARDED}),
 * purely to drive a "quest completed" toast and sound.
 *
 * @param toastTitle the toast's title line, already resolved on the server (the quest's
 *                   {@code toast_overrides.completed} or the default)
 * @param questTitle the title of the quest that was turned in
 */
public record ClientboundQuestRewardedPayload(Component toastTitle, Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestRewardedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_rewarded"));

    private static final StreamCodec<ByteBuf, Component> COMPONENT = ByteBufCodecs.fromCodec(ComponentSerialization.CODEC);

    public static final StreamCodec<ByteBuf, ClientboundQuestRewardedPayload> STREAM_CODEC = StreamCodec.composite(
            COMPONENT, ClientboundQuestRewardedPayload::toastTitle,
            COMPONENT, ClientboundQuestRewardedPayload::questTitle,
            ClientboundQuestRewardedPayload::new);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestRewardedPayload> type() {
        return TYPE;
    }
}
