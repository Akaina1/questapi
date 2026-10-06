package com.ryankshah.questapi.impl.network;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestRewardedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestStartedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestUnlockedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncDefinitionsPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncProgressPayload;
import com.ryankshah.questapi.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-side packet handling logic shared by both loaders. Each loader module registers these
 * methods as the handler bodies for its own payload registration API.
 */
public final class QuestNetworking {

    private QuestNetworking() {
    }

    public static void sendDefinitions(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new ClientboundSyncDefinitionsPayload(
                List.copyOf(QuestApi.registry().categories()),
                List.copyOf(QuestApi.registry().quests()),
                new ManualQuestActions(DevConfig.allowManualStart(), DevConfig.allowManualAbandon(),
                        DevConfig.allowManualClaim(), DevConfig.allowManualDeliver())
        ));
    }

    public static void sendProgress(ServerPlayer player) {
        // Packets are encoded asynchronously on a Netty thread well after this call returns, so a
        // live reference to the mutable PlayerQuestData would race further server-thread mutations
        // (e.g. the next tick's objective updates) and throw ConcurrentModificationException mid
        // -encode. A snapshot copy is cheap and makes that impossible.
        Services.NETWORK.sendToPlayer(player, new ClientboundSyncProgressPayload(QuestApi.manager().dataFor(player).copy()));
    }

    public static void sendQuestCompleted(ServerPlayer player, Quest quest) {
        if (!DevConfig.showCompletedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestCompletedPayload(quest.title()));
    }

    public static void sendQuestRewarded(ServerPlayer player, Quest quest) {
        if (!DevConfig.showRewardedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestRewardedPayload(quest.title()));
    }

    public static void sendQuestStarted(ServerPlayer player, Quest quest) {
        if (!DevConfig.showStartedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestStartedPayload(quest.title()));
    }

    public static void sendQuestUnlocked(ServerPlayer player, Quest quest) {
        if (!DevConfig.showUnlockedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestUnlockedPayload(quest.title()));
    }

    public static void onPlayerJoined(ServerPlayer player) {
        QuestApi.manager().refreshAvailability(player);
        sendDefinitions(player);
        sendProgress(player);
    }

    public static void handleRequestSync(ServerPlayer player) {
        sendDefinitions(player);
        sendProgress(player);
    }

    public static void handleStartQuest(ServerPlayer player, Identifier questId) {
        if (!DevConfig.allowManualStart()) {
            logBlocked("start", player, questId);
            return;
        }
        if (QuestApi.manager().startQuest(player, questId)) {
            sendProgress(player);
        }
    }

    public static void handleAbandonQuest(ServerPlayer player, Identifier questId) {
        if (!DevConfig.allowManualAbandon()) {
            logBlocked("abandon", player, questId);
            return;
        }
        if (QuestApi.manager().abandonQuest(player, questId)) {
            sendProgress(player);
        }
    }

    public static void handleClaimReward(ServerPlayer player, Identifier questId) {
        if (!DevConfig.allowManualClaim()) {
            logBlocked("claim", player, questId);
            return;
        }
        if (QuestApi.manager().claimRewards(player, questId)) {
            sendProgress(player);
        }
    }

    public static void handleDeliverItems(ServerPlayer player, Identifier questId, int objectiveIndex, int amount) {
        if (!DevConfig.allowManualDeliver()) {
            logBlocked("deliver", player, questId);
            return;
        }
        if (QuestApi.manager().deliverItems(player, questId, objectiveIndex, amount)) {
            sendProgress(player);
        }
    }

    private static void logBlocked(String action, ServerPlayer player, Identifier questId) {
        QuestApi.LOG.debug("Ignored manual quest {} of {} from {}: disabled in questapi.properties",
                action, questId, player.getName().getString());
    }
}
