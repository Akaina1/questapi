package com.ryankshah.questapi.impl;

import com.ryankshah.questapi.api.QuestManager;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.event.QuestEventListener;
import com.ryankshah.questapi.api.quest.event.QuestEvents;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveEventKeys;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.api.quest.objective.impl.DeliverItemObjective;
import com.ryankshah.questapi.impl.persistence.QuestSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public final class QuestManagerImpl implements QuestManager {

    private final QuestRegistry registry;
    private MinecraftServer server;
    private QuestSavedData savedData;

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
        if (progress != null) {
            return progress.state();
        }
        return passesPrerequisites(player, quest) ? QuestState.AVAILABLE : QuestState.LOCKED;
    }

    @Override
    public QuestProgress getProgress(ServerPlayer player, Identifier questId) {
        QuestProgress progress = dataFor(player).get(questId);
        return progress != null ? progress : QuestProgress.locked();
    }

    private boolean passesPrerequisites(ServerPlayer player, Quest quest) {
        if (quest.prerequisites().isEmpty()) {
            return true;
        }
        QuestContext ctx = new QuestContext(player, this);
        for (QuestCondition condition : quest.prerequisites()) {
            if (!condition.test(ctx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void refreshAvailability(ServerPlayer player) {
        PlayerQuestData data = dataFor(player);
        boolean changed = false;
        for (Quest quest : registry.quests()) {
            QuestProgress progress = data.get(quest.id());
            if (progress != null && progress.state() != QuestState.LOCKED && progress.state() != QuestState.ABANDONED) {
                continue;
            }
            boolean unlocked = passesPrerequisites(player, quest);
            if (!unlocked) {
                if (progress == null) {
                    continue;
                }
                progress.setState(QuestState.LOCKED);
                changed = true;
                continue;
            }
            if (quest.autoActivate()) {
                activateQuest(player, quest, data.getOrCreate(quest.id()));
            } else {
                data.getOrCreate(quest.id()).setState(QuestState.AVAILABLE);
            }
            changed = true;
            for (QuestEventListener listener : QuestEvents.listeners()) {
                listener.onQuestUnlocked(player, quest);
            }
        }
        if (changed) {
            markDirty();
        }
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
        progress.objectives().clear();
        progress.setState(QuestState.ABANDONED);
        progress.setStartedAt(0);
        markDirty();
        refreshAvailability(player);
        registry.getQuest(questId).ifPresent(quest -> {
            for (QuestEventListener listener : QuestEvents.listeners()) {
                listener.onQuestReset(player, quest);
            }
        });
        return true;
    }

    @Override
    public boolean failQuest(ServerPlayer player, Identifier questId) {
        Quest quest = registry.getQuest(questId).orElse(null);
        QuestProgress progress = dataFor(player).get(questId);
        if (quest == null || progress == null || progress.state() != QuestState.ACTIVE) {
            return false;
        }
        progress.setState(QuestState.FAILED);
        markDirty();
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestFailed(player, quest);
        }
        return true;
    }

    @Override
    public void resetQuest(ServerPlayer player, Identifier questId) {
        PlayerQuestData data = dataFor(player);
        data.progress().remove(questId);
        markDirty();
        refreshAvailability(player);
        registry.getQuest(questId).ifPresent(quest -> {
            for (QuestEventListener listener : QuestEvents.listeners()) {
                listener.onQuestReset(player, quest);
            }
        });
    }

    @Override
    public boolean claimRewards(ServerPlayer player, Identifier questId) {
        Quest quest = registry.getQuest(questId).orElse(null);
        PlayerQuestData data = dataFor(player);
        QuestProgress progress = data.get(questId);
        if (quest == null || progress == null || progress.state() != QuestState.COMPLETED) {
            return false;
        }
        QuestContext ctx = new QuestContext(player, this);
        for (var reward : quest.rewards()) {
            reward.grant(ctx);
        }
        progress.setState(QuestState.REWARDED);
        progress.setRewardedAt(System.currentTimeMillis());
        progress.setRewardedAtDay(currentGameDay());
        markDirty();
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onRewardClaimed(player, quest);
        }
        refreshAvailability(player);
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
        if (!progress.objectiveUnlocked(quest, objectiveIndex)) {
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
        // World-state conditions (time of day, weather, biome) can flip from false to true without
        // any discrete quest event to hang a recheck off of, unlike every other built-in condition -
        // so availability needs to be polled here too, not just at login/claim/reset/abandon.
        refreshAvailability(player);
        checkRepeatableResets(player);
        applyEvent(player, ObjectiveEventKeys.TICK, 0);
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
    private void checkRepeatableResets(ServerPlayer player) {
        if (server == null) {
            return;
        }
        PlayerQuestData data = dataFor(player);
        List<Identifier> toReset = new java.util.ArrayList<>();
        for (Map.Entry<Identifier, QuestProgress> entry : data.progress().entrySet()) {
            if (entry.getValue().state() != QuestState.REWARDED) {
                continue;
            }
            Quest quest = registry.getQuest(entry.getKey()).orElse(null);
            if (quest == null || !quest.repeatable()) {
                continue;
            }
            if (repeatableCooldownElapsed(quest, entry.getValue())) {
                toReset.add(entry.getKey());
            }
        }
        if (toReset.isEmpty()) {
            return;
        }
        for (Identifier questId : toReset) {
            data.progress().remove(questId);
        }
        markDirty();
        refreshAvailability(player);
        for (Identifier questId : toReset) {
            registry.getQuest(questId).ifPresent(quest -> {
                for (QuestEventListener listener : QuestEvents.listeners()) {
                    listener.onQuestReset(player, quest);
                }
            });
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
        PlayerQuestData data = dataFor(player);
        QuestContext ctx = new QuestContext(player, this);
        boolean changed = false;
        for (Map.Entry<Identifier, QuestProgress> entry : data.progress().entrySet()) {
            QuestProgress progress = entry.getValue();
            if (progress.state() != QuestState.ACTIVE) {
                continue;
            }
            Quest quest = registry.getQuest(entry.getKey()).orElse(null);
            if (quest == null) {
                continue;
            }
            List<ObjectiveDefinition> objectives = quest.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!progress.objectiveUnlocked(quest, i)) {
                    break;
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
    }

    private boolean checkCompletion(ServerPlayer player, Quest quest, QuestProgress progress) {
        if (progress.state() != QuestState.ACTIVE) {
            return false;
        }
        List<ObjectiveDefinition> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!progress.objective(i).complete()) {
                return false;
            }
        }
        progress.setState(QuestState.COMPLETED);
        progress.setCompletedAt(System.currentTimeMillis());
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
