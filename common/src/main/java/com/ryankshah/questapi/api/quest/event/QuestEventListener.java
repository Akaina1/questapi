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

    /**
     * Fired when a quest's rewards are claimed: the normal rewards of a completed quest, or the
     * failure rewards of a failed one (in which case the quest is still {@code FAILED}).
     */
    default void onRewardClaimed(ServerPlayer player, Quest quest) {
    }

    /**
     * Fired when an {@code ACTIVE} quest is failed through {@code QuestManager#failQuest}.
     */
    default void onQuestFailed(ServerPlayer player, Quest quest) {
    }

    /**
     * Fired when an {@code ACTIVE} quest is abandoned through {@code QuestManager#abandonQuest}
     * (the quest book, an NPC dialog or a command). The quest's progress is already gone and it
     * reads as available again. {@link #onQuestReset} does not fire for an abandon, so it keeps
     * meaning an administrative or automatic reset.
     */
    default void onQuestAbandoned(ServerPlayer player, Quest quest) {
    }

    default void onQuestReset(ServerPlayer player, Quest quest) {
    }

    /**
     * Fired when a player's block position differs from the last time it was checked. Fires at most
     * once per tick per player and never while they stand still, so a listener that reacts to
     * movement (entering a biome or area) only does work while the player is actually moving. Keep
     * implementations cheap.
     */
    default void onPlayerMoved(ServerPlayer player) {
    }
}
