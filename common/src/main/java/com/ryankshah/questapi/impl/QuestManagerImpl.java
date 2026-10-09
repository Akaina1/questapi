package com.ryankshah.questapi.impl;

import com.ryankshah.questapi.api.QuestManager;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.FailTrigger;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.QuestTimeLimit;
import com.ryankshah.questapi.api.quest.QuestlineDefinition;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.api.quest.RewardChoice;
import com.ryankshah.questapi.api.quest.TimeLimitUnit;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import com.ryankshah.questapi.api.quest.event.QuestEventListener;
import com.ryankshah.questapi.api.quest.event.QuestEvents;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveEventKeys;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.api.quest.objective.impl.DeliverItemObjective;
import com.ryankshah.questapi.impl.persistence.QuestSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public final class QuestManagerImpl implements QuestManager {

    /**
     * Identifies one player's running timer for one quest.
     */
    private record TimerKey(UUID player, Identifier quest) {
    }

    /**
     * The clocks as last read for a timer, so only genuinely elapsed time is counted. The game time
     * lets a sample left over from before the player logged out be recognised and discarded.
     */
    private record ClockSample(long clockTime, long gameTime) {
    }

    /**
     * A sample older than this many ticks means the timer was not being ticked (the player was
     * offline), so the clock difference since then must not be charged to the quest.
     */
    private static final long MAX_SAMPLE_AGE_TICKS = 2L;

    /**
     * How a questline stands for one player: open for quests to be started, not opened yet, or
     * closed for good.
     */
    private enum QuestlineStatus {
        OPEN, NOT_YET, CLOSED
    }

    /**
     * The per-player lists and last-seen values that keep the per-tick work independent of how many
     * quests exist. The id lists are rebuilt from the saved progress (which only holds started
     * quests) whenever a quest changes state, and once per second as a safety net, and are replaced
     * rather than modified so a caller iterating one is never disturbed.
     */
    private static final class PlayerRuntime {
        boolean dirty = true;
        List<Identifier> active = List.of();
        List<Identifier> timed = List.of();
        List<Identifier> repeatable = List.of();
        long lastBlockPos = Long.MIN_VALUE;
        boolean inventorySampled;
        int inventorySignature;
        int experienceLevel = -1;
    }

    /** How often (in ticks) poll-based objectives and repeatable resets are re-evaluated. */
    private static final int SLOW_TICK_INTERVAL = 20;
    /** How often (in ticks) the inventory is compared, and only while an item condition exists. */
    private static final int INVENTORY_SAMPLE_INTERVAL = 10;
    /** Deepest questline nesting followed; also stops a parent cycle from looping forever. */
    private static final int MAX_QUESTLINE_DEPTH = 8;

    private final QuestRegistry registry;
    private final Map<TimerKey, ClockSample> clockSamples = new HashMap<>();
    private final Map<UUID, PlayerRuntime> runtimes = new HashMap<>();
    private MinecraftServer server;
    private QuestSavedData savedData;
    private long lastWorldCheckTick = -1L;
    private boolean worldSampled;
    private boolean lastBright;
    private int lastWeather;

    public QuestManagerImpl(QuestRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void attachServer(MinecraftServer server) {
        this.server = server;
        this.savedData = server.getDataStorage().computeIfAbsent(QuestSavedData.TYPE);
    }

    @Override
    public void detachServer() {
        this.server = null;
        this.savedData = null;
        this.clockSamples.clear();
        this.runtimes.clear();
        this.lastWorldCheckTick = -1L;
        this.worldSampled = false;
    }

    @Override
    public PlayerQuestData dataFor(ServerPlayer player) {
        return savedData.getOrCreate(player.getUUID());
    }

    @Override
    public QuestState getState(ServerPlayer player, Identifier questId) {
        Quest quest = registry.getQuest(questId).orElse(null);
        if (quest == null) {
            return QuestState.LOCKED;
        }
        QuestProgress progress = dataFor(player).get(questId);
        if (progress != null && progress.state().stored()) {
            return progress.state();
        }
        return computedState(player, quest);
    }

    @Override
    public QuestProgress getProgress(ServerPlayer player, Identifier questId) {
        QuestProgress progress = dataFor(player).get(questId);
        return progress != null ? progress : QuestProgress.locked();
    }

    /**
     * The state of a quest the player has not started, worked out from their current conditions.
     * Nothing is saved, so the answer can never be stale.
     */
    private QuestState computedState(ServerPlayer player, Quest quest) {
        OptionalInt chapter = registry.chapterOf(quest.id());
        if (chapter.isPresent()) {
            switch (chapterStatus(player, chapter.getAsInt())) {
                case CLOSED -> {
                    return QuestState.PERMANENTLY_LOCKED;
                }
                case NOT_YET -> {
                    return QuestState.LOCKED;
                }
                default -> {
                }
            }
        }
        QuestContext ctx = new QuestContext(player, this);
        if (quest.questline().isPresent()) {
            switch (questlineStatus(ctx, quest.questline().get())) {
                case CLOSED -> {
                    return QuestState.PERMANENTLY_LOCKED;
                }
                case NOT_YET -> {
                    return QuestState.LOCKED;
                }
                default -> {
                }
            }
        }
        return passesPrerequisites(ctx, quest) ? QuestState.AVAILABLE : QuestState.LOCKED;
    }

    /**
     * How a chapter stands for one player. It is closed once any of its final quests was handed in,
     * and not open yet while the chapter before it is still running. Only stored quest states are
     * read, so this never recurses into {@link #getState}. Chapter 1 is never "not yet".
     */
    private QuestlineStatus chapterStatus(ServerPlayer player, int chapter) {
        if (isChapterEnded(player, chapter)) {
            return QuestlineStatus.CLOSED;
        }
        if (chapter > 1 && !isChapterEnded(player, chapter - 1)) {
            return QuestlineStatus.NOT_YET;
        }
        return QuestlineStatus.OPEN;
    }

    private boolean isChapterEnded(ServerPlayer player, int chapter) {
        PlayerQuestData data = dataFor(player);
        for (Quest finalQuest : registry.chapterFinals(chapter)) {
            QuestProgress progress = data.get(finalQuest.id());
            if (progress != null && progress.state() == QuestState.REWARDED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs when a chapter final quest was just handed in. Every quest the player started in that
     * chapter and has not finished (still active, or completed but not claimed) fails, then the
     * quests of the next chapter are re-checked so they announce themselves and auto-start. Quests
     * that were never started need nothing: they read as permanently locked on their own.
     */
    private void endChapter(ServerPlayer player, Quest finalQuest) {
        OptionalInt chapter = registry.chapterOf(finalQuest.id());
        if (chapter.isEmpty() || !isChapterEnded(player, chapter.getAsInt())) {
            return;
        }
        PlayerQuestData data = dataFor(player);
        for (Quest quest : new ArrayList<>(registry.questsInChapter(chapter.getAsInt()))) {
            QuestProgress progress = data.get(quest.id());
            if (progress != null && (progress.state() == QuestState.ACTIVE || progress.state() == QuestState.COMPLETED)) {
                failQuest(player, quest.id(), true);
            }
        }
        evaluateQuests(player, registry.questsInChapter(chapter.getAsInt() + 1));
    }

    /**
     * Checks a questline and then the ones it is nested in. A closed one wins over everything,
     * because a closed parent closes all of its children.
     */
    private QuestlineStatus questlineStatus(QuestContext ctx, Identifier questlineId) {
        boolean opened = true;
        Identifier id = questlineId;
        for (int depth = 0; id != null && depth < MAX_QUESTLINE_DEPTH; depth++) {
            QuestlineDefinition line = registry.getQuestline(id).orElse(null);
            if (line == null) {
                break;
            }
            for (QuestCondition condition : line.closesWhen()) {
                if (condition.test(ctx)) {
                    return QuestlineStatus.CLOSED;
                }
            }
            if (opened) {
                for (QuestCondition condition : line.opensWhen()) {
                    if (!condition.test(ctx)) {
                        opened = false;
                        break;
                    }
                }
            }
            id = line.parent().orElse(null);
        }
        return opened ? QuestlineStatus.OPEN : QuestlineStatus.NOT_YET;
    }

    @Override
    public boolean isQuestlineClosed(ServerPlayer player, Identifier questlineId) {
        return questlineStatus(new QuestContext(player, this), questlineId) == QuestlineStatus.CLOSED;
    }

    /**
     * Whether every prerequisite passes. Conditions that only read stored history (flags, quest
     * states) run first, so a quest whose flags are not met never reaches the live checks of the
     * world, the position or the inventory.
     */
    private boolean passesPrerequisites(QuestContext ctx, Quest quest) {
        List<QuestCondition> prerequisites = quest.prerequisites();
        if (prerequisites.isEmpty()) {
            return true;
        }
        for (QuestCondition condition : prerequisites) {
            if (!condition.live() && !condition.test(ctx)) {
                return false;
            }
        }
        for (QuestCondition condition : prerequisites) {
            if (condition.live() && !condition.test(ctx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void refreshAvailability(ServerPlayer player) {
        runtimes.remove(player.getUUID());
        migrateStoredAvailability(dataFor(player));
        evaluateQuests(player, registry.quests());
    }

    /**
     * Older saves stored LOCKED, AVAILABLE and ABANDONED entries for every quest. Those states are
     * computed now, so the entries are dropped, remembering which quests the player had already been
     * told about so they are not announced a second time.
     */
    private void migrateStoredAvailability(PlayerQuestData data) {
        boolean changed = false;
        Iterator<Map.Entry<Identifier, QuestProgress>> entries = data.progress().entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Identifier, QuestProgress> entry = entries.next();
            QuestState state = entry.getValue().state();
            if (state.stored()) {
                continue;
            }
            if (state == QuestState.AVAILABLE || state == QuestState.ABANDONED) {
                data.markUnlockSeen(entry.getKey());
            }
            entries.remove();
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    @Override
    public void notifyTrigger(ServerPlayer player, Identifier trigger) {
        if (savedData == null) {
            return;
        }
        List<Quest> quests = registry.questsForTrigger(trigger);
        if (!quests.isEmpty()) {
            evaluateQuests(player, quests);
        }
        List<QuestlineDefinition> closing = registry.questlinesClosedBy(trigger);
        if (closing.isEmpty()) {
            return;
        }
        for (QuestlineDefinition line : closing) {
            if (isQuestlineClosed(player, line.id())) {
                failStartedQuestsIn(player, line.id());
            }
        }
    }

    /**
     * Fails every started quest in a closed questline, including quests in questlines nested inside
     * it. Retryable quests are reset by the failure and then read as permanently locked.
     */
    private void failStartedQuestsIn(ServerPlayer player, Identifier questlineId) {
        PlayerQuestData data = dataFor(player);
        for (Quest quest : new ArrayList<>(registry.questsInQuestline(questlineId))) {
            QuestProgress progress = data.get(quest.id());
            if (progress != null && progress.state() == QuestState.ACTIVE) {
                failQuest(player, quest.id());
            }
        }
    }

    /**
     * Re-checks only the given quests: announces the first unlock of each one that is now available
     * and starts those marked {@code autoActivate}. A quest the player already started is skipped.
     * The announcement is remembered, so a quest that becomes available and unavailable again (a
     * night-only quest, say) is only announced once.
     */
    private void evaluateQuests(ServerPlayer player, Collection<Quest> quests) {
        PlayerQuestData data = dataFor(player);
        for (Quest quest : quests) {
            QuestProgress progress = data.get(quest.id());
            if (progress != null && progress.state().stored()) {
                continue;
            }
            if (computedState(player, quest) != QuestState.AVAILABLE) {
                continue;
            }
            boolean firstUnlock = data.markUnlockSeen(quest.id());
            if (quest.autoActivate()) {
                activateQuest(player, quest, data.getOrCreate(quest.id()));
            }
            if (firstUnlock) {
                for (QuestEventListener listener : QuestEvents.listeners()) {
                    listener.onQuestUnlocked(player, quest);
                }
            }
            if (firstUnlock || quest.autoActivate()) {
                markDirty();
            }
        }
    }

    private void notifyQuestChanged(ServerPlayer player, Quest quest) {
        notifyTrigger(player, TriggerKeys.quest(quest.id()));
    }

    private PlayerRuntime runtimeFor(ServerPlayer player) {
        return runtimes.computeIfAbsent(player.getUUID(), id -> new PlayerRuntime());
    }

    private void invalidateRuntime(ServerPlayer player) {
        runtimeFor(player).dirty = true;
    }

    /**
     * The runtime lists for a player, rebuilt first if a quest changed state since they were built.
     * Only started quests exist in the saved progress, so the rebuild cost follows what the player
     * has touched and not the size of the quest list.
     */
    private PlayerRuntime currentRuntime(ServerPlayer player) {
        PlayerRuntime runtime = runtimeFor(player);
        if (!runtime.dirty) {
            return runtime;
        }
        List<Identifier> active = new ArrayList<>();
        List<Identifier> timed = new ArrayList<>();
        List<Identifier> repeatable = new ArrayList<>();
        for (Map.Entry<Identifier, QuestProgress> entry : dataFor(player).progress().entrySet()) {
            QuestState state = entry.getValue().state();
            if (state != QuestState.ACTIVE && state != QuestState.REWARDED) {
                continue;
            }
            Quest quest = registry.getQuest(entry.getKey()).orElse(null);
            if (quest == null) {
                continue;
            }
            if (state == QuestState.ACTIVE) {
                active.add(entry.getKey());
                if (quest.failure().flatMap(QuestFailureRules::timeLimit).isPresent()) {
                    timed.add(entry.getKey());
                }
            } else if (quest.repeatable()) {
                repeatable.add(entry.getKey());
            }
        }
        runtime.active = active;
        runtime.timed = timed;
        runtime.repeatable = repeatable;
        runtime.dirty = false;
        return runtime;
    }

    private void activateQuest(ServerPlayer player, Quest quest, QuestProgress progress) {
        QuestContext ctx = new QuestContext(player, this);
        List<ObjectiveDefinition> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveProgress op = ObjectiveProgress.empty();
            op.setBaseline(objectives.get(i).captureBaseline(ctx));
            progress.objectives().put(i, op);
        }
        progress.setState(QuestState.ACTIVE);
        progress.setStartedAt(System.currentTimeMillis());
        progress.setElapsedTicks(0L);
        clockSamples.remove(new TimerKey(player.getUUID(), quest.id()));
        if (DevConfig.autoTrackStartedQuests()) {
            dataFor(player).trackIfRoom(quest.id());
        }
        invalidateRuntime(player);
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestStarted(player, quest);
        }
    }

    @Override
    public boolean startQuest(ServerPlayer player, Identifier questId) {
        Quest quest = registry.getQuest(questId).orElse(null);
        if (quest == null || getState(player, questId) != QuestState.AVAILABLE) {
            return false;
        }
        PlayerQuestData data = dataFor(player);
        activateQuest(player, quest, data.getOrCreate(questId));
        markDirty();
        return true;
    }

    @Override
    public boolean abandonQuest(ServerPlayer player, Identifier questId) {
        PlayerQuestData data = dataFor(player);
        QuestProgress progress = data.get(questId);
        if (progress == null || progress.state() != QuestState.ACTIVE) {
            return false;
        }
        data.progress().remove(questId);
        clockSamples.remove(new TimerKey(player.getUUID(), questId));
        markDirty();
        invalidateRuntime(player);
        Quest quest = registry.getQuest(questId).orElse(null);
        if (quest == null) {
            return true;
        }
        evaluateQuests(player, List.of(quest));
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestAbandoned(player, quest);
        }
        notifyQuestChanged(player, quest);
        return true;
    }

    @Override
    public boolean toggleTracked(ServerPlayer player, Identifier questId) {
        PlayerQuestData data = dataFor(player);
        if (!data.isTracked(questId) && getState(player, questId) != QuestState.ACTIVE) {
            return false;
        }
        data.toggleTracked(questId);
        markDirty();
        return true;
    }

    @Override
    public boolean failQuest(ServerPlayer player, Identifier questId) {
        return failQuest(player, questId, false);
    }

    /**
     * Fails a quest. Only a chapter ending passes {@code includeCompleted}, because a quest whose
     * objectives are done but whose rewards were not claimed is lost with its chapter.
     */
    private boolean failQuest(ServerPlayer player, Identifier questId, boolean includeCompleted) {
        Quest quest = registry.getQuest(questId).orElse(null);
        QuestProgress progress = dataFor(player).get(questId);
        if (quest == null || progress == null) {
            return false;
        }
        boolean failable = progress.state() == QuestState.ACTIVE
                || (includeCompleted && progress.state() == QuestState.COMPLETED);
        if (!failable) {
            return false;
        }
        progress.setState(QuestState.FAILED);
        clockSamples.remove(new TimerKey(player.getUUID(), questId));
        markDirty();
        invalidateRuntime(player);
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestFailed(player, quest);
        }
        if (quest.retryable()) {
            resetQuest(player, questId);
        } else {
            notifyQuestChanged(player, quest);
        }
        return true;
    }

    /**
     * Whether {@code eventKey} fails {@code quest} right now, per its {@code fail_on} triggers. A
     * trigger limited to a step only counts while that step is unlocked and still incomplete.
     */
    private static boolean triggersFailure(Quest quest, QuestProgress progress, Identifier eventKey) {
        QuestFailureRules rules = quest.failure().orElse(null);
        if (rules == null) {
            return false;
        }
        for (FailTrigger trigger : rules.failOn()) {
            if (!trigger.event().equals(eventKey)) {
                continue;
            }
            if (trigger.duringStep().isEmpty()) {
                return true;
            }
            int step = trigger.duringStep().get();
            if (step >= quest.objectives().size() || !progress.objectiveUnlocked(quest, step)) {
                continue;
            }
            ObjectiveProgress stepProgress = progress.objectives().get(step);
            if (stepProgress == null || !stepProgress.complete()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the time this player has spent on each active timed quest and fails any quest whose
     * limit has run out.
     */
    private void checkTimeLimits(ServerPlayer player, PlayerRuntime runtime) {
        if (server == null || runtime.timed.isEmpty()) {
            return;
        }
        PlayerQuestData data = dataFor(player);
        List<Identifier> expired = new ArrayList<>();
        boolean changed = false;
        for (Identifier questId : runtime.timed) {
            QuestProgress progress = data.get(questId);
            if (progress == null || progress.state() != QuestState.ACTIVE) {
                continue;
            }
            Quest quest = registry.getQuest(questId).orElse(null);
            QuestTimeLimit limit = quest == null ? null : quest.failure().flatMap(QuestFailureRules::timeLimit).orElse(null);
            if (limit == null) {
                continue;
            }
            long elapsed = progress.elapsedTicks() + elapsedSinceLastTick(player, questId, limit.unit());
            if (elapsed != progress.elapsedTicks()) {
                progress.setElapsedTicks(elapsed);
                changed = true;
            }
            if (elapsed >= limit.unit().toTicks(limit.amount(), DevConfig.ticksPerGameDay())) {
                expired.add(questId);
            }
        }
        if (changed) {
            markDirty();
        }
        for (Identifier questId : expired) {
            failQuest(player, questId);
        }
    }

    /**
     * Ticks of {@code unit}'s clock that passed since this timer was last ticked. Day-clock units
     * only ever count forward movement of the overworld clock, so sleeping counts, a paused clock
     * counts nothing, and setting the time backwards never extends a deadline.
     */
    private long elapsedSinceLastTick(ServerPlayer player, Identifier questId, TimeLimitUnit unit) {
        if (!unit.usesDayClock()) {
            return 1L;
        }
        ServerLevel overworld = server.overworld();
        long clockTime = overworld.getOverworldClockTime();
        long gameTime = overworld.getGameTime();
        ClockSample previous = clockSamples.put(new TimerKey(player.getUUID(), questId), new ClockSample(clockTime, gameTime));
        if (previous == null || gameTime - previous.gameTime() > MAX_SAMPLE_AGE_TICKS) {
            return 0L;
        }
        return Math.max(0L, clockTime - previous.clockTime());
    }

    @Override
    public void resetQuest(ServerPlayer player, Identifier questId) {
        PlayerQuestData data = dataFor(player);
        data.progress().remove(questId);
        clockSamples.remove(new TimerKey(player.getUUID(), questId));
        markDirty();
        invalidateRuntime(player);
        Quest quest = registry.getQuest(questId).orElse(null);
        if (quest == null) {
            return;
        }
        evaluateQuests(player, List.of(quest));
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestReset(player, quest);
        }
        notifyQuestChanged(player, quest);
    }

    @Override
    public boolean claimRewards(ServerPlayer player, Identifier questId) {
        return claim(player, questId, null);
    }

    @Override
    public boolean claimRewards(ServerPlayer player, Identifier questId, String choiceId) {
        return claim(player, questId, choiceId == null ? "" : choiceId);
    }

    /**
     * Shared claim path. A {@code null} choice is the plain claim, which a quest with reward choices
     * refuses; otherwise the choice must be one of the quest's own.
     */
    private boolean claim(ServerPlayer player, Identifier questId, String choiceId) {
        Quest quest = registry.getQuest(questId).orElse(null);
        PlayerQuestData data = dataFor(player);
        QuestProgress progress = data.get(questId);
        if (quest == null || progress == null) {
            return false;
        }
        if (progress.state() == QuestState.FAILED) {
            return claimFailureRewards(player, quest, progress);
        }
        if (progress.state() != QuestState.COMPLETED) {
            return false;
        }
        RewardChoice chosen = null;
        if (!quest.rewardChoices().isEmpty()) {
            if (choiceId == null) {
                return false;
            }
            chosen = quest.rewardChoice(choiceId).orElse(null);
            if (chosen == null) {
                return false;
            }
        }
        QuestContext ctx = new QuestContext(player, this);
        for (var reward : quest.rewards()) {
            reward.grant(ctx);
        }
        if (chosen != null) {
            for (var reward : chosen.rewards()) {
                reward.grant(ctx);
            }
            progress.setChosenReward(chosen.id());
        }
        progress.setState(QuestState.REWARDED);
        progress.setRewardedAt(System.currentTimeMillis());
        progress.setRewardedAtDay(currentGameDay());
        markDirty();
        invalidateRuntime(player);
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onRewardClaimed(player, quest);
        }
        notifyQuestChanged(player, quest);
        if (quest.chapterFinal()) {
            endChapter(player, quest);
        }
        return true;
    }

    /**
     * Grants a failed quest's failure rewards once. The quest stays {@code FAILED} so failure
     * branching keeps working; only the claimed flag changes.
     */
    private boolean claimFailureRewards(ServerPlayer player, Quest quest, QuestProgress progress) {
        if (progress.failureRewardsClaimed() || quest.failureRewards().isEmpty()) {
            return false;
        }
        QuestContext ctx = new QuestContext(player, this);
        for (var reward : quest.failureRewards()) {
            reward.grant(ctx);
        }
        progress.setFailureRewardsClaimed(true);
        markDirty();
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onRewardClaimed(player, quest);
        }
        return true;
    }

    @Override
    public boolean deliverItems(ServerPlayer player, Identifier questId, int objectiveIndex, int amount) {
        Quest quest = registry.getQuest(questId).orElse(null);
        PlayerQuestData data = dataFor(player);
        QuestProgress progress = data.get(questId);
        if (quest == null || progress == null || progress.state() != QuestState.ACTIVE) {
            return false;
        }
        List<ObjectiveDefinition> objectives = quest.objectives();
        if (objectiveIndex < 0 || objectiveIndex >= objectives.size()) {
            return false;
        }
        if (!progress.objectiveOpen(quest, objectiveIndex)) {
            return false;
        }
        ObjectiveDefinition definition = objectives.get(objectiveIndex);
        if (!(definition instanceof DeliverItemObjective deliver)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.countItem(deliver.item()) < amount) {
            return false;
        }
        removeItems(inventory, deliver.item(), amount);
        QuestContext ctx = new QuestContext(player, this);
        ObjectiveProgress op = progress.objective(objectiveIndex);
        int newAmount = definition.evaluate(ctx, op, ObjectiveEventKeys.ITEM_DELIVERED, amount);
        boolean newlyComplete = op.updateCurrent(newAmount, definition.targetAmount());
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onObjectiveProgressChanged(player, quest, objectiveIndex, op);
            if (newlyComplete) {
                listener.onObjectiveCompleted(player, quest, objectiveIndex);
            }
        }
        checkCompletion(player, quest, progress);
        markDirty();
        return true;
    }

    private void removeItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                inventory.removeItem(i, take);
                remaining -= take;
            }
        }
    }

    @Override
    public void tickObjectives(ServerPlayer player) {
        if (server == null || savedData == null) {
            return;
        }
        long tick = server.getTickCount();
        sampleWorldTriggers(tick);
        PlayerRuntime runtime = runtimeFor(player);
        sampleMovement(player, runtime);
        sampleExperience(player, runtime);
        sampleInventory(player, runtime, tick);

        boolean slowTick = (tick + player.getId()) % SLOW_TICK_INTERVAL == 0;
        if (slowTick) {
            runtime.dirty = true;
        }
        runtime = currentRuntime(player);
        checkTimeLimits(player, runtime);
        if (slowTick) {
            checkRepeatableResets(player, runtime);
            applyEvent(player, ObjectiveEventKeys.TICK, 0);
        }
    }

    /**
     * Fires {@link TriggerKeys#POSITION} when the player's block position changed since the last
     * tick. A single long comparison, and nothing at all while the player stands still.
     */
    private void sampleMovement(ServerPlayer player, PlayerRuntime runtime) {
        long packed = player.blockPosition().asLong();
        if (packed == runtime.lastBlockPos) {
            return;
        }
        boolean firstSample = runtime.lastBlockPos == Long.MIN_VALUE;
        runtime.lastBlockPos = packed;
        if (firstSample) {
            return;
        }
        notifyTrigger(player, TriggerKeys.POSITION);
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onPlayerMoved(player);
        }
    }

    private void sampleExperience(ServerPlayer player, PlayerRuntime runtime) {
        int level = player.experienceLevel;
        if (level == runtime.experienceLevel) {
            return;
        }
        boolean firstSample = runtime.experienceLevel < 0;
        runtime.experienceLevel = level;
        if (!firstSample) {
            notifyTrigger(player, TriggerKeys.EXPERIENCE_LEVEL);
        }
    }

    /**
     * Compares a cheap signature of the inventory a few times a second, and only while some quest
     * actually has an item condition. A change fires {@link TriggerKeys#INVENTORY}.
     */
    private void sampleInventory(ServerPlayer player, PlayerRuntime runtime, long tick) {
        if ((tick + player.getId()) % INVENTORY_SAMPLE_INTERVAL != 0
                || registry.questsForTrigger(TriggerKeys.INVENTORY).isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int signature = 1;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                signature = 31 * signature + slot;
                signature = 31 * signature + stack.getItem().hashCode();
                signature = 31 * signature + stack.getCount();
            }
        }
        boolean changed = runtime.inventorySampled && signature != runtime.inventorySignature;
        runtime.inventorySignature = signature;
        runtime.inventorySampled = true;
        if (changed) {
            notifyTrigger(player, TriggerKeys.INVENTORY);
        }
    }

    /**
     * Once a second, compares the overworld's day/night and weather with the last reading and fires
     * {@link TriggerKeys#TIME_OF_DAY} or {@link TriggerKeys#WEATHER} for every player when they
     * changed. This runs once per tick however many players there are.
     */
    private void sampleWorldTriggers(long tick) {
        if (tick == lastWorldCheckTick) {
            return;
        }
        lastWorldCheckTick = tick;
        if (tick % SLOW_TICK_INTERVAL != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        boolean bright = overworld.isBrightOutside();
        int weather = overworld.isThundering() ? 2 : overworld.isRaining() ? 1 : 0;
        boolean timeChanged = worldSampled && bright != lastBright;
        boolean weatherChanged = worldSampled && weather != lastWeather;
        lastBright = bright;
        lastWeather = weather;
        worldSampled = true;
        if (timeChanged) {
            notifyAllPlayers(TriggerKeys.TIME_OF_DAY);
        }
        if (weatherChanged) {
            notifyAllPlayers(TriggerKeys.WEATHER);
        }
    }

    private void notifyAllPlayers(Identifier trigger) {
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            notifyTrigger(player, trigger);
        }
    }

    /**
     * The world's age in in-game days (total elapsed ticks / 24000), used as the clock for
     * {@link ResetMode#IN_GAME_DAY} repeatable quests. Unlike the vanilla day/night cycle clock,
     * this never jumps forward when players sleep, and only advances while the server is running.
     */
    private long currentGameDay() {
        return server != null ? server.overworld().getGameTime() / 24000L : 0L;
    }

    private ResetMode resolveResetMode(Quest quest) {
        return quest.resetMode() != null ? quest.resetMode() : DevConfig.defaultResetMode();
    }

    private boolean repeatableCooldownElapsed(Quest quest, QuestProgress progress) {
        if (resolveResetMode(quest) == ResetMode.WALL_CLOCK) {
            long elapsedMillis = System.currentTimeMillis() - progress.rewardedAt();
            return elapsedMillis >= quest.resetAmount() * 3_600_000L;
        }
        long elapsedDays = currentGameDay() - progress.rewardedAtDay();
        return elapsedDays >= quest.resetAmount();
    }

    /**
     * Moves any {@code REWARDED} repeatable quest whose cooldown has elapsed back to {@code
     * AVAILABLE} (or {@code LOCKED}, if its prerequisites have since regressed).
     */
    private void checkRepeatableResets(ServerPlayer player, PlayerRuntime runtime) {
        if (server == null || runtime.repeatable.isEmpty()) {
            return;
        }
        PlayerQuestData data = dataFor(player);
        List<Quest> toReset = new ArrayList<>();
        for (Identifier questId : runtime.repeatable) {
            QuestProgress progress = data.get(questId);
            if (progress == null || progress.state() != QuestState.REWARDED) {
                continue;
            }
            Quest quest = registry.getQuest(questId).orElse(null);
            if (quest == null || !quest.repeatable()) {
                continue;
            }
            if (repeatableCooldownElapsed(quest, progress)) {
                toReset.add(quest);
            }
        }
        if (toReset.isEmpty()) {
            return;
        }
        for (Quest quest : toReset) {
            data.progress().remove(quest.id());
        }
        markDirty();
        invalidateRuntime(player);
        evaluateQuests(player, toReset);
        for (Quest quest : toReset) {
            for (QuestEventListener listener : QuestEvents.listeners()) {
                listener.onQuestReset(player, quest);
            }
            notifyQuestChanged(player, quest);
        }
    }

    @Override
    public void pushObjectiveEvent(ServerPlayer player, Identifier eventKey, int amount) {
        applyEvent(player, eventKey, amount);
    }

    private void applyEvent(ServerPlayer player, Identifier eventKey, int amount) {
        if (server == null) {
            return;
        }
        List<Identifier> active = currentRuntime(player).active;
        if (active.isEmpty()) {
            return;
        }
        PlayerQuestData data = dataFor(player);
        QuestContext ctx = new QuestContext(player, this);
        boolean changed = false;
        List<Identifier> failedQuests = new ArrayList<>();
        for (Identifier questId : active) {
            QuestProgress progress = data.get(questId);
            if (progress == null || progress.state() != QuestState.ACTIVE) {
                continue;
            }
            Quest quest = registry.getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            if (triggersFailure(quest, progress, eventKey)) {
                failedQuests.add(questId);
                continue;
            }
            List<ObjectiveDefinition> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!progress.objectiveUnlocked(quest, i)) {
                    break;
                }
                if (progress.objectiveFrozen(quest, i)) {
                    continue;
                }
                ObjectiveDefinition definition = objectives.get(i);
                ObjectiveProgress op = progress.objective(i);
                int previous = op.current();
                int newAmount = definition.evaluate(ctx, op, eventKey, amount);
                boolean newlyComplete = op.updateCurrent(newAmount, definition.targetAmount());
                if (op.current() != previous) {
                    changed = true;
                    for (QuestEventListener listener : QuestEvents.listeners()) {
                        listener.onObjectiveProgressChanged(player, quest, i, op);
                    }
                }
                if (newlyComplete) {
                    for (QuestEventListener listener : QuestEvents.listeners()) {
                        listener.onObjectiveCompleted(player, quest, i);
                    }
                }
            }
            if (checkCompletion(player, quest, progress)) {
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        for (Identifier questId : failedQuests) {
            failQuest(player, questId);
        }
    }

    private boolean checkCompletion(ServerPlayer player, Quest quest, QuestProgress progress) {
        if (progress.state() != QuestState.ACTIVE) {
            return false;
        }
        if (!progress.objectivesMet(quest)) {
            return false;
        }
        progress.setState(QuestState.COMPLETED);
        progress.setCompletedAt(System.currentTimeMillis());
        invalidateRuntime(player);
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestCompleted(player, quest);
        }
        return true;
    }

    private void markDirty() {
        if (savedData != null) {
            savedData.markDirty();
        }
    }
}
