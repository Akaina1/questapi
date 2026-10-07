package com.ryankshah.questapi.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;

import java.util.HashMap;
import java.util.Map;

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
            Codec.LONG.optionalFieldOf("elapsedTicks", 0L).forGetter(QuestProgress::elapsedTicks)
    ).apply(instance, QuestProgress::new));

    private QuestState state;
    private final Map<Integer, ObjectiveProgress> objectives;
    private long startedAt;
    private long completedAt;
    private long rewardedAt;
    private long rewardedAtDay;
    private long elapsedTicks;

    public QuestProgress(QuestState state, Map<Integer, ObjectiveProgress> objectives, long startedAt, long completedAt, long rewardedAt, long rewardedAtDay, long elapsedTicks) {
        this.state = state;
        this.objectives = new HashMap<>(objectives);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.rewardedAt = rewardedAt;
        this.rewardedAtDay = rewardedAtDay;
        this.elapsedTicks = elapsedTicks;
    }

    public static QuestProgress locked() {
        return new QuestProgress(QuestState.LOCKED, Map.of(), 0, 0, 0, 0, 0);
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

    /**
     * Whether objective {@code index} is currently open for progress. Always {@code true} unless the
     * quest is {@link Quest#sequential()}, in which case every earlier objective must be complete.
     * Read-only, so it is safe to call from the client GUI as well as the server.
     */
    public boolean objectiveUnlocked(Quest quest, int index) {
        if (!quest.sequential()) {
            return true;
        }
        for (int i = 0; i < index; i++) {
            ObjectiveProgress previous = objectives.get(i);
            if (previous == null || !previous.complete()) {
                return false;
            }
        }
        return true;
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
     * Deep copy, safe to hand to something that will read it later (or on another thread) without
     * racing further mutations - e.g. a network payload that gets encoded asynchronously well after
     * the call that queued it returns.
     */
    public QuestProgress copy() {
        Map<Integer, ObjectiveProgress> copiedObjectives = new HashMap<>();
        for (Map.Entry<Integer, ObjectiveProgress> entry : objectives.entrySet()) {
            copiedObjectives.put(entry.getKey(), entry.getValue().copy());
        }
        return new QuestProgress(state, copiedObjectives, startedAt, completedAt, rewardedAt, rewardedAtDay, elapsedTicks);
    }
}
