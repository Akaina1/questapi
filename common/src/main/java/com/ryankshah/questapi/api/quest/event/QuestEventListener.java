package com.ryankshah.questapi.api.quest.event;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hook interface for observing quest lifecycle events from other mods. Register an implementation
 * via {@link QuestEvents#register}; every method has a no-op default so listeners only need to
 * override the events they actually care about.
 * <p>
 * All callbacks fire server-side, synchronously with the state change that triggered them.
 */
public interface QuestEventListener {

    /**
     * Fired once when a quest is added to the registry, typically during mod initialization.
     */
    default void onQuestRegistered(Quest quest) {
    }

    default void onQuestStarted(ServerPlayer player, Quest quest) {
    }

    /**
     * Fired once when a quest becomes visible to a player for the first time - either newly
     * {@code AVAILABLE}, or jumping straight to {@code ACTIVE} for an {@code autoActivate} quest.
     * Not fired for a quest that was already {@code AVAILABLE}/{@code ACTIVE} from a previous session.
     */
    default void onQuestUnlocked(ServerPlayer player, Quest quest) {
    }

    default void onObjectiveProgressChanged(ServerPlayer player, Quest quest, int objectiveIndex, ObjectiveProgress progress) {
    }

    default void onObjectiveCompleted(ServerPlayer player, Quest quest, int objectiveIndex) {
    }

    default void onQuestCompleted(ServerPlayer player, Quest quest) {
    }

    default void onRewardClaimed(ServerPlayer player, Quest quest) {
    }

    /**
     * Fired when an {@code ACTIVE} quest is failed through {@code QuestManager#failQuest}.
     */
    default void onQuestFailed(ServerPlayer player, Quest quest) {
    }

    default void onQuestReset(ServerPlayer player, Quest quest) {
    }
}
