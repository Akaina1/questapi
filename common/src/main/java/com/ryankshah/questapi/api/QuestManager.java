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
     * Returns the current lifecycle state of a quest for a player. Quests the player has started
     * report their stored state. Every other quest reports {@link QuestState#PERMANENTLY_LOCKED} if
     * its questline is closed, otherwise {@link QuestState#AVAILABLE} or {@link QuestState#LOCKED}
     * depending on whether its prerequisites pass right now. Those three are computed on each call
     * and never stored, so they can never be out of date.
     */
    QuestState getState(ServerPlayer player, Identifier questId);

    QuestProgress getProgress(ServerPlayer player, Identifier questId);

    /**
     * Evaluates every registered quest once: fires the first-time unlock notification and
     * auto-activates quests marked {@code autoActivate} whose prerequisites now pass, and migrates
     * saved data from versions that stored LOCKED/AVAILABLE entries. This is a full pass over all
     * quests, so call it only at login; use {@link #notifyTrigger} when one specific thing changed.
     */
    void refreshAvailability(ServerPlayer player);

    /**
     * Reports that something a condition may depend on changed for this player (a flag, a quest
     * state, the inventory, the time of day, ...). Only quests that list {@code trigger} through
     * {@code QuestCondition#triggers()} are re-evaluated, and questlines closed by it fail their
     * started quests.
     *
     * @see com.ryankshah.questapi.api.quest.condition.TriggerKeys
     */
    void notifyTrigger(ServerPlayer player, Identifier trigger);

    /**
     * Whether {@code questlineId}, or any questline it is nested in, is closed for this player.
     */
    boolean isQuestlineClosed(ServerPlayer player, Identifier questlineId);

    /**
     * Manually starts an {@code AVAILABLE} quest, transitioning it to {@code ACTIVE} and capturing
     * baselines for its poll-based objectives. No-op (returns {@code false}) if the quest is not
     * currently available.
     */
    boolean startQuest(ServerPlayer player, Identifier questId);

    /**
     * Abandons an {@code ACTIVE} quest, discarding its progress and returning it to its computed
     * {@code AVAILABLE}/{@code LOCKED} state. No-op if the quest is not active.
     */
    boolean abandonQuest(ServerPlayer player, Identifier questId);

    /**
     * Pins or unpins a quest on the player's HUD tracker, which is saved with their quest data.
     * Pinning is only allowed for an {@code ACTIVE} quest; unpinning is always allowed. Pinning
     * into a full tracker drops the oldest pinned quest. Returns {@code false} if nothing changed.
     */
    boolean toggleTracked(ServerPlayer player, Identifier questId);

    /**
     * Fails an {@code ACTIVE} quest, moving it to {@code FAILED} and keeping its objective progress
     * for display. No-op (returns {@code false}) if the quest is not active. A failed quest only
     * leaves that state through {@link #resetQuest}, except for quests whose failure rules mark them
     * retryable: those are reset immediately after the failure listeners have run. Quests that
     * depend on this quest being failed are unlocked right away.
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
     * <p>
     * A {@code FAILED} quest with {@code failure.rewards} is claimed through the same call: every
     * failure reward is granted once and the quest stays {@code FAILED} (the claim is remembered
     * in {@code QuestProgress#failureRewardsClaimed}). Returns {@code false} when there is nothing
     * left to claim.
     */
    boolean claimRewards(ServerPlayer player, Identifier questId);

    /**
     * Removes {@code amount} matching items from the player's inventory and reports them delivered
     * to the given quest objective. Returns {@code false} if the player does not have enough items
     * or the objective is not a delivery objective.
     */
    boolean deliverItems(ServerPlayer player, Identifier questId, int objectiveIndex, int amount);

    /**
     * Called every server tick by each loader's tick hook. Per tick it only does work that depends
     * on the player's started quests with a time limit, plus a few constant-time checks that fire
     * triggers (block position, inventory contents, experience level, and the overworld's time of
     * day and weather). Poll-based objectives and repeatable-quest resets are re-evaluated once per
     * second, staggered per player. Availability of unstarted quests is never polled.
     */
    void tickObjectives(ServerPlayer player);

    /**
     * Reports a push-based event (block placed, animal tamed, custom third-party events, ...) to
     * every active objective across every active quest for this player. Objectives that don't
     * recognise the event key are unaffected.
     */
    void pushObjectiveEvent(ServerPlayer player, Identifier eventKey, int amount);
}
