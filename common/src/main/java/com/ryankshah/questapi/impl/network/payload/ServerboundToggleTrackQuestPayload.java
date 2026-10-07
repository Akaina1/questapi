package com.ryankshah.questapi.impl.network.payload;

import com.ryankshah.questapi.QuestApi;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Requests that the server pin or unpin a quest on the sending player's HUD tracker.
 */
public record ServerboundToggleTrackQuestPayload(Identifier questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerboundToggleTrackQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "toggle_track_quest"));

    public static final StreamCodec<ByteBuf, ServerboundToggleTrackQuestPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(ServerboundToggleTrackQuestPayload::new, ServerboundToggleTrackQuestPayload::questId);

    @Override
    public CustomPacketPayload.Type<ServerboundToggleTrackQuestPayload> type() {
        return TYPE;
    }
}
