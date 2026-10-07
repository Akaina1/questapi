package com.ryankshah.questapi;

import com.ryankshah.questapi.api.quest.objective.ObjectiveEventKeys;
import com.ryankshah.questapi.command.QuestCommands;
import com.ryankshah.questapi.example.ExampleQuests;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.data.QuestDataLoader;
import com.ryankshah.questapi.impl.network.QuestNetworking;
import com.ryankshah.questapi.impl.network.payload.ClientboundObjectiveCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestFailedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestRewardedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestStartedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestUnlockedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncDefinitionsPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncProgressPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundAbandonQuestPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundClaimRewardPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundDeliverItemsPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundRequestSyncPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundStartQuestPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundToggleTrackQuestPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;

public class QuestApiFabric implements ModInitializer {

    private final QuestDataLoader questDataLoader = new QuestDataLoader();

    @Override
    public void onInitialize() {
        QuestApi.bootstrap();
        DevConfig.load();

        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(QuestDataLoader.ID, questDataLoader);

        PayloadTypeRegistry.clientboundPlay().register(ClientboundSyncDefinitionsPayload.TYPE, ClientboundSyncDefinitionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundSyncProgressPayload.TYPE, ClientboundSyncProgressPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundQuestCompletedPayload.TYPE, ClientboundQuestCompletedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundQuestUnlockedPayload.TYPE, ClientboundQuestUnlockedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundQuestStartedPayload.TYPE, ClientboundQuestStartedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundQuestRewardedPayload.TYPE, ClientboundQuestRewardedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundQuestFailedPayload.TYPE, ClientboundQuestFailedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundObjectiveCompletedPayload.TYPE, ClientboundObjectiveCompletedPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundRequestSyncPayload.TYPE, ServerboundRequestSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundStartQuestPayload.TYPE, ServerboundStartQuestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundToggleTrackQuestPayload.TYPE, ServerboundToggleTrackQuestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundAbandonQuestPayload.TYPE, ServerboundAbandonQuestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundClaimRewardPayload.TYPE, ServerboundClaimRewardPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundDeliverItemsPayload.TYPE, ServerboundDeliverItemsPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestSyncPayload.TYPE,
                (payload, context) -> QuestNetworking.handleRequestSync(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundStartQuestPayload.TYPE,
                (payload, context) -> QuestNetworking.handleStartQuest(context.player(), payload.questId()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundToggleTrackQuestPayload.TYPE,
                (payload, context) -> QuestNetworking.handleToggleTrackQuest(context.player(), payload.questId()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundAbandonQuestPayload.TYPE,
                (payload, context) -> QuestNetworking.handleAbandonQuest(context.player(), payload.questId()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundClaimRewardPayload.TYPE,
                (payload, context) -> QuestNetworking.handleClaimReward(context.player(), payload.questId()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundDeliverItemsPayload.TYPE,
                (payload, context) -> QuestNetworking.handleDeliverItems(context.player(), payload.questId(), payload.objectiveIndex(), payload.amount()));

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> QuestNetworking.onPlayerJoined(listener.player));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                QuestApi.manager().tickObjectives(player);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                QuestApi.manager().pushObjectiveEvent(player, ObjectiveEventKeys.PLAYER_DIED, 1);
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // Item/registry data components are only bound once registries finish loading, which
            // happens after mod init but before this event - so quest content that builds ItemStacks
            // (like the example tree) must register here, not in onInitialize().
            if (DevConfig.isDevMode()) {
                ExampleQuests.registerAll();
            }
            questDataLoader.finalizeAndRegister();
            QuestApi.manager().attachServer(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestApi.manager().detachServer());

        if (DevConfig.isDevMode()) {
            CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> QuestCommands.register(dispatcher));
        }
    }
}
