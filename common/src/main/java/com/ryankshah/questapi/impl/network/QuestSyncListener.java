package com.ryankshah.questapi.impl.network;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.event.QuestEventListener;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the loader-independent {@link QuestEventListener} callbacks to network sync, so that
 * {@code QuestManagerImpl} itself never needs to know networking exists. Only the player's progress
 * snapshot is resent (never the full quest definitions) since only progress can change after login.
 */
public final class QuestSyncListener implements QuestEventListener {

    @Override
    public void onQuestStarted(ServerPlayer player, Quest quest) {
        QuestNetworking.sendProgress(player);
        QuestNetworking.sendQuestStarted(player, quest);
    }

    @Override
    public void onQuestUnlocked(ServerPlayer player, Quest quest) {
        QuestNetworking.sendQuestUnlocked(player, quest);
    }

    @Override
    public void onObjectiveProgressChanged(ServerPlayer player, Quest quest, int objectiveIndex, ObjectiveProgress progress) {
        QuestNetworking.sendProgress(player);
    }

    @Override
    public void onObjectiveCompleted(ServerPlayer player, Quest quest, int objectiveIndex) {
        QuestNetworking.sendObjectiveCompleted(player, quest, objectiveIndex);
    }

    @Override
    public void onQuestCompleted(ServerPlayer player, Quest quest) {
        QuestNetworking.sendProgress(player);
        QuestNetworking.sendQuestCompleted(player, quest);
    }

    @Override
    public void onRewardClaimed(ServerPlayer player, Quest quest) {
        QuestNetworking.sendProgress(player);
        QuestNetworking.sendQuestRewarded(player, quest);
    }

    @Override
    public void onQuestFailed(ServerPlayer player, Quest quest) {
        QuestNetworking.sendProgress(player);
    }

    @Override
    public void onQuestReset(ServerPlayer player, Quest quest) {
        QuestNetworking.sendProgress(player);
    }
}
