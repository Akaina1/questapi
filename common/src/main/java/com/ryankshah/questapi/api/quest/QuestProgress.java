package com.ryankshah.questapi.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable, per-player runtime state for a single quest.
 * <p>
 * Instances live inside {@link PlayerQuestData} and are persisted to disk; the paired
 * {@link Quest} definition is looked up by ID from the {@link com.ryankshah.questapi.api.QuestRegistry}
 * rather than being duplicated here.
 */
public final class QuestProgress {

    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(QuestState::valueOf, Enum::name).fieldOf("state").forGetter(QuestProgress::state),
            Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), ObjectiveProgress.CODEC)
                    .fieldOf("objectives").forGetter(QuestProgress::objectivesRaw),
            Codec.LONG.fieldOf("startedAt").forGetter(QuestProgress::startedAt),
            Codec.LONG.fieldOf("completedAt").forGetter(QuestProgress::completedAt),
            Codec.LONG.fieldOf("rewardedAt").forGetter(QuestProgress::rewardedAt),
            Codec.LONG.optionalFieldOf("rewardedAtDay", 0L).forGetter(QuestProgress::rewardedAtDay),
            Codec.LONG.optionalFieldOf("elapsedTicks", 0L).forGetter(QuestProgress::elapsedTicks),
            Codec.BOOL.optionalFieldOf("failureRewardsClaimed", false).forGetter(QuestProgress::failureRewardsClaimed),
            Codec.STRING.optionalFieldOf("chosenReward").forGetter(QuestProgress::chosenReward)
    ).apply(instance, QuestProgress::new));

    private QuestState state;
    private final Map<Integer, ObjectiveProgress> objectives;
    private long startedAt;
    private long completedAt;
    private long rewardedAt;
    private long rewardedAtDay;
    private long elapsedTicks;
    private boolean failureRewardsClaimed;
    private Optional<String> chosenReward;

    public QuestProgress(QuestState state, Map<Integer, ObjectiveProgress> objectives, long startedAt, long completedAt, long rewardedAt, long rewardedAtDay, long elapsedTicks, boolean failureRewardsClaimed, Optional<String> chosenReward) {
        this.state = state;
        this.objectives = new HashMap<>(objectives);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.rewardedAt = rewardedAt;
        this.rewardedAtDay = rewardedAtDay;
        this.elapsedTicks = elapsedTicks;
        this.failureRewardsClaimed = failureRewardsClaimed;
        this.chosenReward = chosenReward;
    }

    public static QuestProgress locked() {
        return new QuestProgress(QuestState.LOCKED, Map.of(), 0, 0, 0, 0, 0, false, Optional.empty());
    }

    public QuestState state() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = state;
    }

    public Map<Integer, ObjectiveProgress> objectives() {
        return objectives;
    }

    private Map<Integer, ObjectiveProgress> objectivesRaw() {
        return objectives;
    }

    public ObjectiveProgress objective(int index) {
        return objectives.computeIfAbsent(index, i -> ObjectiveProgress.empty());
    }

    private boolean objectiveComplete(int index) {
        ObjectiveProgress progress = objectives.get(index);
        return progress != null && progress.complete();
    }

    /**
     * Whether an entry of the quest's objective list is done: a single objective is complete, or a
     * group has at least its required number of options complete. Read-only, so it is safe to call
     * from the client GUI as well as the server.
     */
    public boolean entryComplete(Quest quest, int entryIndex) {
        ObjectiveEntry entry = quest.objectiveEntries().get(entryIndex);
        int first = quest.firstObjectiveOf(entryIndex);
        int done = 0;
        for (int i = 0; i < entry.options().size(); i++) {
            if (objectiveComplete(first + i)) {
                done++;
            }
        }
        return done >= entry.required();
    }

    /**
     * Whether every entry that is not optional is done, which is what completes the quest.
     */
    public boolean objectivesMet(Quest quest) {
        List<ObjectiveEntry> entries = quest.objectiveEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).optional() && !entryComplete(quest, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether objective {@code index} has reached its step. Always {@code true} unless the quest is
     * {@link Quest#sequential()}, in which case the required entries of every earlier step must be
     * done. Everything in one step opens together, and optional entries stay open until the quest
     * completes. Read-only, so it is safe to call from the client GUI as well as the server.
     */
    public boolean objectiveUnlocked(Quest quest, int index) {
        if (!quest.sequential()) {
            return true;
        }
        int step = quest.stepOfObjective(index);
        List<ObjectiveEntry> entries = quest.objectiveEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).optional() && quest.stepOf(i) < step && !entryComplete(quest, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a group has already been satisfied and this option was left unfinished. Such an
     * option no longer progresses and is shown as passed over.
     */
    public boolean objectiveFrozen(Quest quest, int index) {
        int entryIndex = quest.entryOf(index);
        return quest.objectiveEntries().get(entryIndex).isGroup()
                && !objectiveComplete(index)
                && entryComplete(quest, entryIndex);
    }

    /**
     * Whether objective {@code index} can currently make progress: its step is reached and it is
     * not an unfinished option of a group that is already done.
     */
    public boolean objectiveOpen(Quest quest, int index) {
        return objectiveUnlocked(quest, index) && !objectiveFrozen(quest, index);
    }

    public long startedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long completedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public long rewardedAt() {
        return rewardedAt;
    }

    public void setRewardedAt(long rewardedAt) {
        this.rewardedAt = rewardedAt;
    }

    /**
     * The world's in-game day count (see {@code QuestManagerImpl#currentGameDay}) at the moment this
     * quest was last rewarded. Only meaningful for repeatable quests resolved to
     * {@link ResetMode#IN_GAME_DAY}.
     */
    public long rewardedAtDay() {
        return rewardedAtDay;
    }

    public void setRewardedAtDay(long rewardedAtDay) {
        this.rewardedAtDay = rewardedAtDay;
    }

    /**
     * How much of the quest's time limit has been used, in ticks of the limit's clock (day-clock
     * ticks for game-time units, server ticks for {@link TimeLimitUnit#REAL_SECONDS}). Only
     * accumulates while the quest is active and the player is online.
     */
    public long elapsedTicks() {
        return elapsedTicks;
    }

    public void setElapsedTicks(long elapsedTicks) {
        this.elapsedTicks = elapsedTicks;
    }

    public boolean rewardClaimed() {
        return state == QuestState.REWARDED;
    }

    /**
     * Whether the quest's {@link QuestFailureRules#rewards() failure rewards} were already claimed.
     * The quest stays {@link QuestState#FAILED} afterwards; this flag is what stops a second claim.
     */
    public boolean failureRewardsClaimed() {
        return failureRewardsClaimed;
    }

    public void setFailureRewardsClaimed(boolean failureRewardsClaimed) {
        this.failureRewardsClaimed = failureRewardsClaimed;
    }

    /**
     * The id of the {@link RewardChoice} the player took when the quest was claimed, or empty if the
     * quest has no reward choice or was not claimed yet. Cleared with the rest of the progress when
     * the quest is reset or repeats.
     */
    public Optional<String> chosenReward() {
        return chosenReward;
    }

    public void setChosenReward(String choiceId) {
        this.chosenReward = Optional.ofNullable(choiceId);
    }

    /**
     * Whether claiming this quest's rewards would currently grant something: it is
     * {@link QuestState#COMPLETED}, or it is {@link QuestState#FAILED} with failure rewards that
     * were not claimed yet. Read-only, so it is safe to call from the client GUI as well.
     */
    public boolean claimable(Quest quest) {
        return state == QuestState.COMPLETED
                || (state == QuestState.FAILED && !failureRewardsClaimed && !quest.failureRewards().isEmpty());
    }

    /**
     * Deep copy, safe to hand to something that will read it later (or on another thread) without
     * racing further mutations - e.g. a network payload that gets encoded asynchronously well after
     * the call that queued it returns.
     */
    public QuestProgress copy() {
        Map<Integer, ObjectiveProgress> copiedObjectives = new HashMap<>();
        for (Map.Entry<Integer, ObjectiveProgress> entry : objectives.entrySet()) {
            copiedObjectives.put(entry.getKey(), entry.getValue().copy());
        }
        return new QuestProgress(state, copiedObjectives, startedAt, completedAt, rewardedAt, rewardedAtDay, elapsedTicks, failureRewardsClaimed, chosenReward);
    }
}
