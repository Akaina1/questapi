package com.ryankshah.questapi;

import com.ryankshah.questapi.api.quest.objective.ObjectiveEventKeys;
import com.ryankshah.questapi.command.QuestCommands;
import com.ryankshah.questapi.example.ExampleQuests;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.data.QuestDataLoader;
import com.ryankshah.questapi.impl.network.QuestNetworking;
import com.ryankshah.questapi.impl.network.payload.ClientboundChapterEndingPayload;
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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(QuestApi.MOD_ID)
public class QuestApiNeoForge {

    private final QuestDataLoader questDataLoader = new QuestDataLoader();

    public QuestApiNeoForge(IEventBus modEventBus) {
        QuestApi.bootstrap();
        DevConfig.load();

        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        if (DevConfig.isDevMode()) {
            NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        }

        if (FMLEnvironment.getDist().isClient()) {
            QuestApiNeoForgeClient.init(modEventBus);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ClientboundSyncDefinitionsPayload.TYPE, ClientboundSyncDefinitionsPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleSyncDefinitions(payload));
        registrar.playToClient(ClientboundSyncProgressPayload.TYPE, ClientboundSyncProgressPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleSyncProgress(payload.data()));
        registrar.playToClient(ClientboundQuestCompletedPayload.TYPE, ClientboundQuestCompletedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleQuestCompleted(payload.toastTitle(), payload.questTitle()));
        registrar.playToClient(ClientboundQuestUnlockedPayload.TYPE, ClientboundQuestUnlockedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleQuestUnlocked(payload.questTitle()));
        registrar.playToClient(ClientboundQuestStartedPayload.TYPE, ClientboundQuestStartedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleQuestStarted(payload.toastTitle(), payload.questTitle()));
        registrar.playToClient(ClientboundQuestRewardedPayload.TYPE, ClientboundQuestRewardedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleQuestRewarded(payload.toastTitle(), payload.questTitle()));
        registrar.playToClient(ClientboundQuestFailedPayload.TYPE, ClientboundQuestFailedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleQuestFailed(payload.toastTitle(), payload.questTitle()));
        registrar.playToClient(ClientboundObjectiveCompletedPayload.TYPE, ClientboundObjectiveCompletedPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleObjectiveCompleted(payload.objectiveDescription()));
        registrar.playToClient(ClientboundChapterEndingPayload.TYPE, ClientboundChapterEndingPayload.STREAM_CODEC,
                (payload, context) -> com.ryankshah.questapi.client.network.ClientQuestNetworking.handleChapterEnding(payload.title(), payload.message()));

        registrar.playToServer(ServerboundRequestSyncPayload.TYPE, ServerboundRequestSyncPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleRequestSync((ServerPlayer) context.player()));
        registrar.playToServer(ServerboundStartQuestPayload.TYPE, ServerboundStartQuestPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleStartQuest((ServerPlayer) context.player(), payload.questId()));
        registrar.playToServer(ServerboundToggleTrackQuestPayload.TYPE, ServerboundToggleTrackQuestPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleToggleTrackQuest((ServerPlayer) context.player(), payload.questId()));
        registrar.playToServer(ServerboundAbandonQuestPayload.TYPE, ServerboundAbandonQuestPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleAbandonQuest((ServerPlayer) context.player(), payload.questId()));
        registrar.playToServer(ServerboundClaimRewardPayload.TYPE, ServerboundClaimRewardPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleClaimReward((ServerPlayer) context.player(), payload.questId()));
        registrar.playToServer(ServerboundDeliverItemsPayload.TYPE, ServerboundDeliverItemsPayload.STREAM_CODEC,
                (payload, context) -> QuestNetworking.handleDeliverItems((ServerPlayer) context.player(), payload.questId(), payload.objectiveIndex(), payload.amount()));
    }

    private void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(QuestDataLoader.ID, questDataLoader);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestNetworking.onPlayerJoined(player);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            QuestApi.manager().tickObjectives(player);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestApi.manager().pushObjectiveEvent(player, ObjectiveEventKeys.PLAYER_DIED, 1);
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Item/registry data components are only bound once registries finish loading, which
        // happens after mod construction but before this event - so quest content that builds
        // ItemStacks (like the example tree) must register here, not in the mod constructor.
        if (DevConfig.isDevMode()) {
            ExampleQuests.registerAll();
        }
        questDataLoader.finalizeAndRegister();
        QuestApi.manager().attachServer(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        QuestApi.manager().detachServer();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }
}
