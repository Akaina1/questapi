package com.ryankshah.questapi.impl.network;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestToastOverrides;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.network.payload.ClientboundObjectiveCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestFailedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestRewardedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestStartedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestUnlockedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncDefinitionsPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncProgressPayload;
import com.ryankshah.questapi.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
                        DevConfig.allowManualClaim(), DevConfig.allowManualDeliver()),
                DevConfig.ticksPerGameDay()
        ));
    }

    public static void sendProgress(ServerPlayer player) {
        // Packets are encoded asynchronously on a Netty thread well after this call returns, so a
        // live reference to the mutable PlayerQuestData would race further server-thread mutations
        // (e.g. the next tick's objective updates) and throw ConcurrentModificationException mid
        // -encode. A snapshot copy is cheap and makes that impossible.
        PlayerQuestData data = QuestApi.manager().dataFor(player);
        data.pruneTracked();
        Services.NETWORK.sendToPlayer(player, new ClientboundSyncProgressPayload(data.copy()));
    }

    public static void sendQuestCompleted(ServerPlayer player, Quest quest) {
        if (!DevConfig.showCompletedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestCompletedPayload(
                toastTitle(quest, QuestToastOverrides::ready, "questapi.toast.quest_ready.title"), quest.title()));
    }

    /**
     * The toast's title line: the quest's override if it has one, otherwise the default text.
     */
    private static Component toastTitle(Quest quest, Function<QuestToastOverrides, Optional<Component>> override, String defaultKey) {
        return quest.toastOverrides().flatMap(override).orElseGet(() -> Component.translatable(defaultKey));
    }

    /**
     * Toasts a finished objective. When it was the quest's last open objective, the "ready to turn
     * in" toast fires right after and would overwrite this one, so this one is skipped in that case.
     */
    public static void sendObjectiveCompleted(ServerPlayer player, Quest quest, int objectiveIndex) {
        if (!DevConfig.showObjectiveCompletedToast()) {
            return;
        }
        if (DevConfig.showCompletedToast() && allObjectivesComplete(player, quest)) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundObjectiveCompletedPayload(
                quest.objectives().get(objectiveIndex).describe()));
    }

    private static boolean allObjectivesComplete(ServerPlayer player, Quest quest) {
        QuestProgress progress = QuestApi.manager().getProgress(player, quest.id());
        for (int i = 0; i < quest.objectives().size(); i++) {
            ObjectiveProgress objective = progress.objectives().get(i);
            if (objective == null || !objective.complete()) {
                return false;
            }
        }
        return true;
    }

    public static void sendQuestRewarded(ServerPlayer player, Quest quest) {
        if (!DevConfig.showRewardedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestRewardedPayload(
                toastTitle(quest, QuestToastOverrides::completed, "questapi.toast.quest_rewarded.title"), quest.title()));
    }

    public static void sendQuestStarted(ServerPlayer player, Quest quest) {
        if (!DevConfig.showStartedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestStartedPayload(
                toastTitle(quest, QuestToastOverrides::started, "questapi.toast.quest_started.title"), quest.title()));
    }

    public static void sendQuestFailed(ServerPlayer player, Quest quest) {
        if (!DevConfig.showFailedToast()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundQuestFailedPayload(
                toastTitle(quest, QuestToastOverrides::failed, "questapi.toast.quest_failed.title"), quest.title()));
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

    public static void handleToggleTrackQuest(ServerPlayer player, Identifier questId) {
        if (QuestApi.manager().toggleTracked(player, questId)) {
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
