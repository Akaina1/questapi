package com.ryankshah.questapi.impl.network;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestToastOverrides;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.network.payload.ClientboundChapterEndingPayload;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-side packet handling logic shared by both loaders. Each loader module registers these
 * methods as the handler bodies for its own payload registration API.
 */
public final class QuestNetworking {

    private QuestNetworking() {
    }

    /**
     * How many quest definitions go into one packet. A clientbound custom payload is limited to about
     * 1 MiB and a definition is roughly 0.5 to 1 KB, so this keeps each packet far below the limit.
     */
    public static final int DEFINITIONS_PER_PACKET = 200;

    /**
     * Per player, the quest definitions the client has already been sent. Only touched on the server
     * thread, and reset by {@link #sendDefinitions(ServerPlayer)}.
     */
    private static final Map<UUID, Set<Identifier>> SENT_DEFINITIONS = new HashMap<>();

    /**
     * Sends the categories and settings and tells the client to forget every quest definition. The
     * definitions of the player's own quests follow with the next {@link #sendProgress}.
     */
    public static void sendDefinitions(ServerPlayer player) {
        SENT_DEFINITIONS.remove(player.getUUID());
        Services.NETWORK.sendToPlayer(player, new ClientboundSyncDefinitionsPayload(
                List.copyOf(QuestApi.registry().categories()),
                List.of(),
                manualActions(),
                DevConfig.ticksPerGameDay(),
                true
        ));
    }

    private static ManualQuestActions manualActions() {
        return new ManualQuestActions(DevConfig.allowManualStart(), DevConfig.allowManualAbandon(),
                DevConfig.allowManualDeliver());
    }

    /**
     * Sends the definition of every quest the player has progress in and the client does not have
     * yet, in packets of {@link #DEFINITIONS_PER_PACKET}. Quests the player has not started are never
     * sent, so the packet size depends on the quest log and not on how many quests exist.
     */
    private static void sendMissingDefinitions(ServerPlayer player, PlayerQuestData data) {
        Set<Identifier> sent = SENT_DEFINITIONS.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        List<Quest> missing = new ArrayList<>();
        for (Identifier questId : data.progress().keySet()) {
            if (sent.contains(questId)) {
                continue;
            }
            Optional<Quest> quest = QuestApi.registry().getQuest(questId);
            if (quest.isPresent()) {
                missing.add(quest.get());
            }
        }
        for (int from = 0; from < missing.size(); from += DEFINITIONS_PER_PACKET) {
            List<Quest> batch = List.copyOf(missing.subList(from, Math.min(from + DEFINITIONS_PER_PACKET, missing.size())));
            Services.NETWORK.sendToPlayer(player, new ClientboundSyncDefinitionsPayload(
                    List.of(), batch, manualActions(), DevConfig.ticksPerGameDay(), false));
            for (Quest quest : batch) {
                sent.add(quest.id());
            }
        }
    }

    public static void sendProgress(ServerPlayer player) {
        // Packets are encoded asynchronously on a Netty thread well after this call returns, so a
        // live reference to the mutable PlayerQuestData would race further server-thread mutations
        // (e.g. the next tick's objective updates) and throw ConcurrentModificationException mid
        // -encode. A snapshot copy is cheap and makes that impossible.
        PlayerQuestData data = QuestApi.manager().dataFor(player);
        data.pruneTracked();
        sendMissingDefinitions(player, data);
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
        return QuestApi.manager().getProgress(player, quest.id()).objectivesMet(quest);
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

    /**
     * Warns that handing in a chapter final quest ends its chapter. One static message for every
     * chapter: the chapter number and the finale quest's title are the only parts that change.
     */
    public static void sendChapterEnding(ServerPlayer player, Quest quest) {
        if (!DevConfig.showChapterEndingToast()) {
            return;
        }
        OptionalInt chapter = QuestApi.registry().chapterOf(quest.id());
        if (chapter.isEmpty()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ClientboundChapterEndingPayload(
                Component.translatable("questapi.toast.chapter_ending.title"),
                Component.translatable("questapi.toast.chapter_ending.message", chapter.getAsInt(), quest.title())));
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
        if (!QuestApi.registry().getQuest(questId).map(Quest::abandonable).orElse(false)) {
            QuestApi.LOG.debug("Ignored manual quest abandon of {} from {}: the quest is not abandonable",
                    questId, player.getName().getString());
            return;
        }
        if (QuestApi.manager().abandonQuest(player, questId)) {
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
