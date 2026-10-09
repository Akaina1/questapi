package com.ryankshah.questapi.impl.network.payload;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.impl.network.QuestCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Carries category and quest definitions to a client. Quest definitions are sent per player and only
 * for quests the player has progress in (started, completed, rewarded or failed), because that is all
 * the quest book shows; every other quest stays on the server until it is accepted.
 * <p>
 * A payload with {@code replaceQuests} set is the first one after login (or after a re-sync request):
 * the client discards everything it knew and takes the categories, settings and quests from it. A
 * payload without it only adds or replaces the quests it lists and leaves the rest untouched, so a
 * large quest log can be sent in several packets under the size limit of a custom payload (about 1 MiB).
 */
public record ClientboundSyncDefinitionsPayload(List<QuestCategory> categories, List<Quest> quests,
                                                ManualQuestActions manualActions,
                                                int ticksPerGameDay, boolean replaceQuests) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundSyncDefinitionsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "sync_definitions"));

    private static final StreamCodec<ByteBuf, ManualQuestActions> MANUAL_ACTIONS_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ManualQuestActions::start,
            ByteBufCodecs.BOOL, ManualQuestActions::abandon,
            ByteBufCodecs.BOOL, ManualQuestActions::deliver,
            ManualQuestActions::new
    );

    public static final StreamCodec<ByteBuf, ClientboundSyncDefinitionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(QuestCategory.CODEC.listOf()), ClientboundSyncDefinitionsPayload::categories,
            ByteBufCodecs.fromCodec(QuestCodecs.questCodec(QuestApi.registry()).listOf()), ClientboundSyncDefinitionsPayload::quests,
            MANUAL_ACTIONS_CODEC, ClientboundSyncDefinitionsPayload::manualActions,
            ByteBufCodecs.VAR_INT, ClientboundSyncDefinitionsPayload::ticksPerGameDay,
            ByteBufCodecs.BOOL, ClientboundSyncDefinitionsPayload::replaceQuests,
            ClientboundSyncDefinitionsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<ClientboundSyncDefinitionsPayload> type() {
        return TYPE;
    }
}
