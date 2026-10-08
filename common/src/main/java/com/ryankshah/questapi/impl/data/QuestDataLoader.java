package com.ryankshah.questapi.impl.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.FailTrigger;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.QuestlineDefinition;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.impl.QuestFailedCondition;
import com.ryankshah.questapi.example.ExampleQuests;
import com.ryankshah.questapi.impl.DevConfig;
import com.ryankshah.questapi.impl.network.QuestCodecs;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads quest categories and quest definitions from datapacks, as an alternative (or complement) to
 * registering them in Java.
 * <p>
 * Files live at {@code data/<namespace>/questapi/categories/<path>.json} and
 * {@code data/<namespace>/questapi/quests/<path>.json} and
 * {@code data/<namespace>/questapi/questlines/<path>.json}, using exactly the JSON shape produced by
 * {@link QuestCategory#CODEC}, {@link QuestCodecs#questCodec} and {@link QuestCodecs#questlineCodec}. A quest's ID and category come
 * from the JSON content itself, not the file path - the path is purely organisational, so datapack
 * authors are free to group files into folders however they like.
 * <p>
 * This runs as an ordinary server data reload listener, but only reads the raw JSON during the
 * reload itself - item data components are not bound yet at that point (unlike what recipes/loot
 * tables get away with, quest icons/rewards eagerly build real {@code ItemStack}s). The actual
 * decode into {@link Quest}/{@link QuestCategory} objects, and registration, happens in
 * {@link #finalizeAndRegister()}, which each loader calls from its server-starting hook - the same
 * proven-safe point {@code ExampleQuests} registers from.
 */
public final class QuestDataLoader extends SimplePreparableReloadListener<QuestDataLoader.RawData> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_data");

    private static final FileToIdConverter CATEGORIES = FileToIdConverter.json("questapi/categories");
    private static final FileToIdConverter QUESTS = FileToIdConverter.json("questapi/quests");
    private static final FileToIdConverter QUESTLINES = FileToIdConverter.json("questapi/questlines");

    /** Deepest questline nesting followed; also stops a parent cycle from looping forever. */
    private static final int MAX_QUESTLINE_DEPTH = 8;

    private final Set<Identifier> previousCategories = new HashSet<>();
    private final Set<Identifier> previousQuests = new HashSet<>();
    private final Set<Identifier> previousQuestlines = new HashSet<>();
    private volatile RawData pending = new RawData(Map.of(), Map.of(), Map.of());

    public record RawData(Map<Identifier, JsonElement> categories, Map<Identifier, JsonElement> quests,
                          Map<Identifier, JsonElement> questlines) {
    }

    @Override
    protected RawData prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new RawData(readRaw(manager, CATEGORIES), readRaw(manager, QUESTS), readRaw(manager, QUESTLINES));
    }

    private static Map<Identifier, JsonElement> readRaw(ResourceManager manager, FileToIdConverter lister) {
        Map<Identifier, JsonElement> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                result.put(file, StrictJsonParser.parse(reader));
            } catch (IOException | RuntimeException e) {
                QuestApi.LOG.error("Couldn't read quest data file '{}'", file, e);
            }
        }
        return result;
    }

    @Override
    protected void apply(RawData raw, ResourceManager manager, ProfilerFiller profiler) {
        // Just stash it - decoding into real Quest/QuestCategory objects needs item data components,
        // which are not bound yet during a data reload. See finalizeAndRegister().
        this.pending = raw;
    }

    /**
     * Decodes whatever the most recent reload produced and (re)registers it, replacing whatever this
     * loader registered last time. Call from a server-starting hook, after registries/components are
     * fully bound - never from the reload listener itself.
     */
    public void finalizeAndRegister() {
        QuestRegistry registry = QuestApi.registry();
        boolean devMode = DevConfig.isDevMode();

        Map<Identifier, QuestCategory> categories = decodeCategories(pending.categories(), devMode);
        for (Identifier id : previousCategories) {
            registry.removeCategory(id);
        }
        previousCategories.clear();
        for (QuestCategory category : categories.values()) {
            registry.registerCategory(category);
            previousCategories.add(category.id());
        }

        Map<Identifier, QuestlineDefinition> questlines = decodeQuestlines(pending.questlines(), registry, devMode);
        for (Identifier id : previousQuestlines) {
            registry.removeQuestline(id);
        }
        previousQuestlines.clear();
        for (QuestlineDefinition questline : questlines.values()) {
            registry.registerQuestline(questline);
            previousQuestlines.add(questline.id());
        }

        Map<Identifier, Quest> quests = decodeQuests(pending.quests(), registry, devMode);
        for (Identifier id : previousQuests) {
            registry.removeQuest(id);
        }
        previousQuests.clear();
        for (Quest quest : quests.values()) {
            registry.registerQuest(quest);
            previousQuests.add(quest.id());
        }

        validateFailureRules(quests.values(), registry);
        validateQuestlines(quests.values(), registry);
        validateChapters(quests.values(), registry);

        if (!categories.isEmpty() || !quests.isEmpty()) {
            QuestApi.LOG.info("Loaded {} quest categor{} and {} quest{} from datapacks",
                    categories.size(), categories.size() == 1 ? "y" : "ies",
                    quests.size(), quests.size() == 1 ? "" : "s");
        }
    }

    /**
     * Logs a warning for failure setups that can never work as written: a {@code during_step} that
     * points past the quest's last objective, failure rewards on a retryable quest, and a
     * {@code quest_failed} prerequisite on a retryable quest (which is reset straight after
     * failing, so it never stays failed).
     */
    private static void validateFailureRules(Collection<Quest> quests, QuestRegistry registry) {
        for (Quest quest : quests) {
            quest.failure().ifPresent(rules -> {
                for (FailTrigger trigger : rules.failOn()) {
                    trigger.duringStep().filter(step -> step >= quest.objectives().size()).ifPresent(step ->
                            QuestApi.LOG.warn("Quest '{}' fails on '{}' during step {}, but it only has {} objective(s)",
                                    quest.id(), trigger.event(), step, quest.objectives().size()));
                }
                if (rules.retryable() && !rules.rewards().isEmpty()) {
                    QuestApi.LOG.warn("Quest '{}' has failure rewards but is retryable, so it is reset as soon as it fails and they can never be claimed",
                            quest.id());
                }
            });
            for (QuestCondition condition : quest.prerequisites()) {
                if (condition instanceof QuestFailedCondition failed
                        && registry.getQuest(failed.requiredQuestId()).filter(Quest::retryable).isPresent()) {
                    QuestApi.LOG.warn("Quest '{}' requires quest '{}' to fail, but that quest is retryable and is reset as soon as it fails",
                            quest.id(), failed.requiredQuestId());
                }
            }
        }
    }

    /**
     * Logs a warning for a quest that names a questline that does not exist, and for a questline
     * whose parent does not exist or whose parent chain loops back on itself.
     */
    private static void validateQuestlines(Collection<Quest> quests, QuestRegistry registry) {
        for (Quest quest : quests) {
            quest.questline().filter(id -> registry.getQuestline(id).isEmpty()).ifPresent(id ->
                    QuestApi.LOG.warn("Quest '{}' is in questline '{}', which is not defined", quest.id(), id));
        }
        for (QuestlineDefinition questline : registry.questlines()) {
            Set<Identifier> seen = new HashSet<>();
            seen.add(questline.id());
            Identifier parentId = questline.parent().orElse(null);
            while (parentId != null) {
                if (registry.getQuestline(parentId).isEmpty()) {
                    QuestApi.LOG.warn("Questline '{}' has parent '{}', which is not defined", questline.id(), parentId);
                    break;
                }
                if (!seen.add(parentId)) {
                    QuestApi.LOG.warn("Questline '{}' has a parent chain that loops back on itself", questline.id());
                    break;
                }
                parentId = registry.getQuestline(parentId).flatMap(QuestlineDefinition::parent).orElse(null);
            }
        }
    }

    /**
     * Logs a warning for chapter setups that cannot work as written: a final quest that belongs to
     * no chapter, a repeatable final quest (it would reopen the chapter when it resets), a chapter
     * below the highest one in use that has no final quest (so the next chapter could never open;
     * this also catches a skipped number), and a quest whose own chapter differs from the one its
     * questline gives.
     */
    private static void validateChapters(Collection<Quest> quests, QuestRegistry registry) {
        for (Quest quest : quests) {
            if (quest.chapterFinal() && registry.chapterOf(quest.id()).isEmpty()) {
                QuestApi.LOG.warn("Quest '{}' is chapter_final but belongs to no chapter, so it ends nothing", quest.id());
            }
            if (quest.chapterFinal() && quest.repeatable()) {
                QuestApi.LOG.warn("Quest '{}' is chapter_final and repeatable, so it would reopen its chapter when it resets", quest.id());
            }
            Integer own = quest.chapter().orElse(null);
            Integer inherited = questlineChapter(quest, registry);
            if (own != null && inherited != null && !own.equals(inherited)) {
                QuestApi.LOG.warn("Quest '{}' is in chapter {} but its questline is in chapter {}; the quest's own chapter wins",
                        quest.id(), own, inherited);
            }
        }
        List<Integer> chapters = registry.chapters();
        if (chapters.isEmpty()) {
            return;
        }
        int highest = chapters.get(chapters.size() - 1);
        for (int chapter = 1; chapter < highest; chapter++) {
            if (registry.chapterFinals(chapter).isEmpty()) {
                QuestApi.LOG.warn("Chapter {} has no chapter_final quest, so it can never end and chapter {} and later never open",
                        chapter, chapter + 1);
            }
        }
    }

    /**
     * The chapter given by the nearest questline in a quest's questline chain, ignoring the quest's
     * own chapter.
     */
    private static Integer questlineChapter(Quest quest, QuestRegistry registry) {
        Identifier lineId = quest.questline().orElse(null);
        for (int depth = 0; lineId != null && depth < MAX_QUESTLINE_DEPTH; depth++) {
            QuestlineDefinition line = registry.getQuestline(lineId).orElse(null);
            if (line == null) {
                return null;
            }
            if (line.chapter().isPresent()) {
                return line.chapter().get();
            }
            lineId = line.parent().orElse(null);
        }
        return null;
    }

    private static Map<Identifier, QuestlineDefinition> decodeQuestlines(Map<Identifier, JsonElement> raw, QuestRegistry registry, boolean devMode) {
        Codec<QuestlineDefinition> codec = QuestCodecs.questlineCodec(registry);
        Map<Identifier, QuestlineDefinition> result = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : raw.entrySet()) {
            Identifier file = entry.getKey();
            if (!devMode && file.getNamespace().equals(ExampleQuests.MOD)) {
                continue;
            }
            codec.parse(JsonOps.INSTANCE, entry.getValue())
                    .ifSuccess(questline -> result.put(questline.id(), questline))
                    .ifError(error -> QuestApi.LOG.error("Couldn't parse questline file '{}': {}", file, error));
        }
        return result;
    }

    private static Map<Identifier, QuestCategory> decodeCategories(Map<Identifier, JsonElement> raw, boolean devMode) {
        Map<Identifier, QuestCategory> result = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : raw.entrySet()) {
            Identifier file = entry.getKey();
            if (!devMode && file.getNamespace().equals(ExampleQuests.MOD)) {
                continue;
            }
            QuestCategory.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .ifSuccess(category -> result.put(category.id(), category))
                    .ifError(error -> QuestApi.LOG.error("Couldn't parse quest category file '{}': {}", file, error));
        }
        return result;
    }

    private static Map<Identifier, Quest> decodeQuests(Map<Identifier, JsonElement> raw, QuestRegistry registry, boolean devMode) {
        Codec<Quest> codec = QuestCodecs.questCodec(registry);
        Map<Identifier, Quest> result = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : raw.entrySet()) {
            Identifier file = entry.getKey();
            if (!devMode && file.getNamespace().equals(ExampleQuests.MOD)) {
                continue;
            }
            try {
                codec.parse(JsonOps.INSTANCE, entry.getValue())
                        .ifSuccess(quest -> result.put(quest.id(), quest))
                        .ifError(error -> QuestApi.LOG.error("Couldn't parse quest file '{}': {}", file, error));
            } catch (RuntimeException e) {
                // A quest the builder rejects (no objectives, nothing but optional objectives, a
                // group nested in a group) is skipped with a clear message instead of failing the
                // whole reload.
                QuestApi.LOG.error("Couldn't load quest file '{}': {}", file, e.getMessage());
            }
        }
        return result;
    }
}
