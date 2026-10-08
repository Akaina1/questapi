package com.ryankshah.questapi.impl;

import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.QuestlineDefinition;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveType;
import com.ryankshah.questapi.api.quest.event.QuestEventListener;
import com.ryankshah.questapi.api.quest.event.QuestEvents;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import com.ryankshah.questapi.api.quest.reward.RewardType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

public final class QuestRegistryImpl implements QuestRegistry {

    private final Map<Identifier, QuestCategory> categories = new LinkedHashMap<>();
    private final Map<Identifier, Quest> quests = new LinkedHashMap<>();
    private final Map<Identifier, ObjectiveType<?>> objectiveTypes = new LinkedHashMap<>();
    private final Map<Identifier, RewardType<?>> rewardTypes = new LinkedHashMap<>();
    private final Map<Identifier, ConditionType<?>> conditionTypes = new LinkedHashMap<>();
    private final Map<Identifier, QuestlineDefinition> questlines = new LinkedHashMap<>();

    /** Deepest questline nesting followed; also stops a parent cycle from looping forever. */
    private static final int MAX_QUESTLINE_DEPTH = 8;

    private Map<Identifier, List<Quest>> triggerIndex = Map.of();
    private Map<Identifier, List<QuestlineDefinition>> closeIndex = Map.of();
    private Map<Identifier, List<Quest>> questlineQuests = Map.of();
    private Map<Identifier, Integer> questChapters = Map.of();
    private Map<Integer, List<Quest>> chapterQuests = Map.of();
    private Map<Integer, List<Quest>> chapterFinalQuests = Map.of();
    private List<Integer> chapterNumbers = List.of();
    private boolean indexDirty = true;

    @Override
    public void registerCategory(QuestCategory category) {
        categories.put(category.id(), category);
    }

    @Override
    public Collection<QuestCategory> categories() {
        return categories.values();
    }

    @Override
    public Optional<QuestCategory> getCategory(Identifier id) {
        return Optional.ofNullable(categories.get(id));
    }

    @Override
    public void removeCategory(Identifier id) {
        categories.remove(id);
    }

    @Override
    public void registerQuest(Quest quest) {
        if (!categories.containsKey(quest.categoryId())) {
            throw new IllegalStateException("Quest " + quest.id() + " references unregistered category " + quest.categoryId());
        }
        quests.put(quest.id(), quest);
        indexDirty = true;
        for (QuestEventListener listener : QuestEvents.listeners()) {
            listener.onQuestRegistered(quest);
        }
    }

    @Override
    public Collection<Quest> quests() {
        return quests.values();
    }

    @Override
    public Optional<Quest> getQuest(Identifier id) {
        return Optional.ofNullable(quests.get(id));
    }

    @Override
    public void removeQuest(Identifier id) {
        quests.remove(id);
        indexDirty = true;
    }

    @Override
    public void registerQuestline(QuestlineDefinition questline) {
        questlines.put(questline.id(), questline);
        indexDirty = true;
    }

    @Override
    public Collection<QuestlineDefinition> questlines() {
        return questlines.values();
    }

    @Override
    public Optional<QuestlineDefinition> getQuestline(Identifier id) {
        return Optional.ofNullable(questlines.get(id));
    }

    @Override
    public void removeQuestline(Identifier id) {
        questlines.remove(id);
        indexDirty = true;
    }

    @Override
    public List<Quest> questsForTrigger(Identifier trigger) {
        ensureIndex();
        return triggerIndex.getOrDefault(trigger, List.of());
    }

    @Override
    public List<QuestlineDefinition> questlinesClosedBy(Identifier trigger) {
        ensureIndex();
        return closeIndex.getOrDefault(trigger, List.of());
    }

    @Override
    public List<Quest> questsInQuestline(Identifier questlineId) {
        ensureIndex();
        return questlineQuests.getOrDefault(questlineId, List.of());
    }

    @Override
    public OptionalInt chapterOf(Identifier questId) {
        ensureIndex();
        Integer chapter = questChapters.get(questId);
        return chapter == null ? OptionalInt.empty() : OptionalInt.of(chapter);
    }

    @Override
    public List<Quest> questsInChapter(int chapter) {
        ensureIndex();
        return chapterQuests.getOrDefault(chapter, List.of());
    }

    @Override
    public List<Quest> chapterFinals(int chapter) {
        ensureIndex();
        return chapterFinalQuests.getOrDefault(chapter, List.of());
    }

    @Override
    public List<Integer> chapters() {
        ensureIndex();
        return chapterNumbers;
    }

    /**
     * Rebuilds the trigger lookups the first time they are needed after the quest or questline
     * definitions changed. The lookups depend only on definitions, never on a player, so one copy
     * serves everyone and a rebuild only happens on registration or reload.
     */
    private void ensureIndex() {
        if (!indexDirty) {
            return;
        }
        Map<Identifier, List<Quest>> byTrigger = new HashMap<>();
        Map<Identifier, List<QuestlineDefinition>> byClose = new HashMap<>();
        Map<Identifier, List<Quest>> byQuestline = new HashMap<>();
        Map<Identifier, Integer> chapterByQuest = new HashMap<>();
        Map<Integer, List<Quest>> byChapter = new TreeMap<>();
        Map<Integer, List<Quest>> finalsByChapter = new HashMap<>();

        for (Quest quest : quests.values()) {
            Set<Identifier> keys = new LinkedHashSet<>();
            for (QuestCondition condition : quest.prerequisites()) {
                keys.addAll(condition.triggers());
            }
            Integer chapter = quest.chapter().orElse(null);
            Identifier lineId = quest.questline().orElse(null);
            for (int depth = 0; lineId != null && depth < MAX_QUESTLINE_DEPTH; depth++) {
                QuestlineDefinition line = questlines.get(lineId);
                if (line == null) {
                    break;
                }
                for (QuestCondition condition : line.opensWhen()) {
                    keys.addAll(condition.triggers());
                }
                byQuestline.computeIfAbsent(lineId, key -> new ArrayList<>()).add(quest);
                if (chapter == null) {
                    chapter = line.chapter().orElse(null);
                }
                lineId = line.parent().orElse(null);
            }
            if (chapter != null) {
                chapterByQuest.put(quest.id(), chapter);
                byChapter.computeIfAbsent(chapter, key -> new ArrayList<>()).add(quest);
                if (quest.chapterFinal()) {
                    finalsByChapter.computeIfAbsent(chapter, key -> new ArrayList<>()).add(quest);
                }
            }
            for (Identifier key : keys) {
                byTrigger.computeIfAbsent(key, k -> new ArrayList<>()).add(quest);
            }
        }

        for (QuestlineDefinition line : questlines.values()) {
            Set<Identifier> keys = new LinkedHashSet<>();
            for (QuestCondition condition : line.closesWhen()) {
                keys.addAll(condition.triggers());
            }
            for (Identifier key : keys) {
                byClose.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
            }
        }

        triggerIndex = byTrigger;
        closeIndex = byClose;
        questlineQuests = byQuestline;
        questChapters = chapterByQuest;
        chapterQuests = byChapter;
        chapterFinalQuests = finalsByChapter;
        chapterNumbers = List.copyOf(byChapter.keySet());
        indexDirty = false;
    }

    @Override
    public Collection<Quest> questsInCategory(Identifier categoryId) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.categoryId().equals(categoryId)) {
                result.add(quest);
            }
        }
        result.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return result;
    }

    @Override
    public <D extends ObjectiveDefinition> void registerObjectiveType(ObjectiveType<D> type) {
        objectiveTypes.put(type.id(), type);
    }

    @Override
    public Optional<ObjectiveType<?>> getObjectiveType(Identifier id) {
        return Optional.ofNullable(objectiveTypes.get(id));
    }

    @Override
    public Collection<ObjectiveType<?>> objectiveTypes() {
        return objectiveTypes.values();
    }

    @Override
    public <R extends QuestReward> void registerRewardType(RewardType<R> type) {
        rewardTypes.put(type.id(), type);
    }

    @Override
    public Optional<RewardType<?>> getRewardType(Identifier id) {
        return Optional.ofNullable(rewardTypes.get(id));
    }

    @Override
    public <C extends QuestCondition> void registerConditionType(ConditionType<C> type) {
        conditionTypes.put(type.id(), type);
    }

    @Override
    public Optional<ConditionType<?>> getConditionType(Identifier id) {
        return Optional.ofNullable(conditionTypes.get(id));
    }

    @Override
    public void clearQuests() {
        quests.clear();
        categories.clear();
        questlines.clear();
        indexDirty = true;
    }
}
