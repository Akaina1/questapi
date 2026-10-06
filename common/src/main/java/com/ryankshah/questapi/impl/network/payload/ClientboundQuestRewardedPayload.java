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
 * @param questTitle the title of the quest that was turned in
 */
public record ClientboundQuestRewardedPayload(Component questTitle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundQuestRewardedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_rewarded"));

    public static final StreamCodec<ByteBuf, ClientboundQuestRewardedPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).map(ClientboundQuestRewardedPayload::new, ClientboundQuestRewardedPayload::questTitle);

    @Override
    public CustomPacketPayload.Type<ClientboundQuestRewardedPayload> type() {
        return TYPE;
    }
}
