package com.ryankshah.questapi.impl.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.FailTrigger;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
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
import java.util.Map;
import java.util.Set;

/**
 * Loads quest categories and quest definitions from datapacks, as an alternative (or complement) to
 * registering them in Java.
 * <p>
 * Files live at {@code data/<namespace>/questapi/categories/<path>.json} and
 * {@code data/<namespace>/questapi/quests/<path>.json}, using exactly the JSON shape produced by
 * {@link QuestCategory#CODEC} and {@link QuestCodecs#questCodec}. A quest's ID and category come
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

    private final Set<Identifier> previousCategories = new HashSet<>();
    private final Set<Identifier> previousQuests = new HashSet<>();
    private volatile RawData pending = new RawData(Map.of(), Map.of());

    public record RawData(Map<Identifier, JsonElement> categories, Map<Identifier, JsonElement> quests) {
    }

    @Override
    protected RawData prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new RawData(readRaw(manager, CATEGORIES), readRaw(manager, QUESTS));
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

        if (!categories.isEmpty() || !quests.isEmpty()) {
            QuestApi.LOG.info("Loaded {} quest categor{} and {} quest{} from datapacks",
                    categories.size(), categories.size() == 1 ? "y" : "ies",
                    quests.size(), quests.size() == 1 ? "" : "s");
        }
    }

    /**
     * Logs a warning for failure setups that can never work as written: a {@code during_step} that
     * points past the quest's last objective, and a {@code quest_failed} prerequisite on a
     * retryable quest (which is reset straight after failing, so it never stays failed).
     */
    private static void validateFailureRules(Collection<Quest> quests, QuestRegistry registry) {
        for (Quest quest : quests) {
            quest.failure().ifPresent(rules -> {
                for (FailTrigger trigger : rules.failOn()) {
                    trigger.duringStep().filter(step -> step >= quest.objectives().size()).ifPresent(step ->
                            QuestApi.LOG.warn("Quest '{}' fails on '{}' during step {}, but it only has {} objective(s)",
                                    quest.id(), trigger.event(), step, quest.objectives().size()));
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
            codec.parse(JsonOps.INSTANCE, entry.getValue())
                    .ifSuccess(quest -> result.put(quest.id(), quest))
                    .ifError(error -> QuestApi.LOG.error("Couldn't parse quest file '{}': {}", file, error));
        }
        return result;
    }
}
