package com.ryankshah.questapi.api;

import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative runtime orchestrator for player quest progress.
 * <p>
 * Every method that mutates state is safe to call repeatedly and idempotently where the method
 * documentation says so; the manager - not the caller - is responsible for guarding against
 * duplicate reward grants, double-starts, etc. All of this is server-side only; the client only ever
 * sees the results via network sync, never drives this logic itself.
 * <p>
 * Obtain the shared instance via {@link com.ryankshah.questapi.QuestApi#manager()}.
 */
public interface QuestManager {

    /**
     * Binds this manager to a running server so it can access world-backed persistence. Called by
     * each loader's server-starting hook.
     */
    void attachServer(MinecraftServer server);

    /**
     * Unbinds this manager from the server. Called by each loader's server-stopping hook.
     */
    void detachServer();

    PlayerQuestData dataFor(ServerPlayer player);

    /**
     * Returns the current lifecycle state of a quest for a player. Quests the player has never
     * interacted with report {@link QuestState#LOCKED} or {@link QuestState#AVAILABLE} depending on
     * whether their prerequisites currently pass, without materialising a stored progress entry.
     */
    QuestState getState(ServerPlayer player, Identifier questId);

    QuestProgress getProgress(ServerPlayer player, Identifier questId);

    /**
     * Recomputes LOCKED/AVAILABLE state for every registered quest against this player's current
     * progress, auto-activating any quest marked {@code autoActivate} whose prerequisites now pass.
     * Call after login and after any quest completion.
     */
    void refreshAvailability(ServerPlayer player);

    /**
     * Manually starts an {@code AVAILABLE} quest, transitioning it to {@code ACTIVE} and capturing
     * baselines for its poll-based objectives. No-op (returns {@code false}) if the quest is not
     * currently available.
     */
    boolean startQuest(ServerPlayer player, Identifier questId);

    /**
     * Abandons an {@code ACTIVE} quest, discarding its progress and returning it to
     * {@code AVAILABLE}/{@code LOCKED}. No-op if the quest is not active.
     */
    boolean abandonQuest(ServerPlayer player, Identifier questId);

    /**
     * Fails an {@code ACTIVE} quest, moving it to {@code FAILED} and keeping its objective progress
     * for display. No-op (returns {@code false}) if the quest is not active. A failed quest only
     * leaves that state through {@link #resetQuest}.
     */
    boolean failQuest(ServerPlayer player, Identifier questId);

    /**
     * Fully resets a quest to {@code LOCKED}/{@code AVAILABLE} regardless of its current state,
     * discarding all progress and reward-claim status. Intended for dev-mode tooling.
     */
    void resetQuest(ServerPlayer player, Identifier questId);

    /**
     * Claims the rewards of a {@code COMPLETED} quest, granting every {@code QuestReward} exactly
     * once and transitioning the quest to {@code REWARDED}. Returns {@code false} if the quest is
     * not completed or its rewards were already claimed.
     */
    boolean claimRewards(ServerPlayer player, Identifier questId);

    /**
     * Removes {@code amount} matching items from the player's inventory and reports them delivered
     * to the given quest objective. Returns {@code false} if the player does not have enough items
     * or the objective is not a delivery objective.
     */
    boolean deliverItems(ServerPlayer player, Identifier questId, int objectiveIndex, int amount);

    /**
     * Re-evaluates every poll-based objective of every {@code ACTIVE} quest for this player. Called
     * roughly once per second by each loader's server tick hook.
     */
    void tickObjectives(ServerPlayer player);

    /**
     * Reports a push-based event (block placed, animal tamed, custom third-party events, ...) to
     * every active objective across every active quest for this player. Objectives that don't
     * recognise the event key are unaffected.
     */
    void pushObjectiveEvent(ServerPlayer player, Identifier eventKey, int amount);
}
