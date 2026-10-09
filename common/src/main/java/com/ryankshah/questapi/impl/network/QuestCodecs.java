package com.ryankshah.questapi.impl.network;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.FailTrigger;
import com.ryankshah.questapi.api.quest.ObjectiveEntry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestDisplay;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestLifecycle;
import com.ryankshah.questapi.api.quest.QuestlineDefinition;
import com.ryankshah.questapi.api.quest.QuestRepeat;
import com.ryankshah.questapi.api.quest.QuestTimeLimit;
import com.ryankshah.questapi.api.quest.QuestToastOverrides;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.api.quest.RewardChoice;
import com.ryankshah.questapi.api.quest.TimeLimitUnit;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveType;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import com.ryankshah.questapi.api.quest.reward.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Builds registry-backed dispatch {@link Codec}s for the extensible objective/reward/condition
 * types, and the full {@link Quest} codec used to synchronise quest definitions to clients.
 * <p>
 * These are built fresh (never cached) because the underlying type registry can still grow after
 * this class is first touched - the registry is only guaranteed stable once every mod has finished
 * its common initialization phase, which is exactly when the first sync actually happens (a player
 * logging in), so laziness here is what makes third-party objective/reward/condition types work
 * regardless of mod load order.
 */
public final class QuestCodecs {

    private QuestCodecs() {
    }

    public static Codec<ObjectiveDefinition> objectiveCodec(QuestRegistry registry) {
        return Identifier.CODEC.dispatch("type", ObjectiveDefinition::typeId, id -> lookupObjective(registry, id));
    }

    /**
     * The {@code type} of a JSON entry that is a group of options instead of a single objective.
     */
    public static final Identifier ANY_OF = Identifier.fromNamespaceAndPath("questapi", "any_of");

    /**
     * The {@code type} of a group whose options must all be completed ({@code {"type":
     * "questapi:all_of", "optional": false, "options": [...]}}).
     */
    public static final Identifier ALL_OF = Identifier.fromNamespaceAndPath("questapi", "all_of");

    private record RawGroup(List<ObjectiveDefinition> options, int count, boolean optional) {
    }

    /**
     * Reads one entry of a quest's objective list. A group is
     * {@code {"type": "questapi:any_of", "count": 1, "optional": false, "options": [...]}}; anything
     * else is a single objective, which may carry {@code "optional": true} next to its own fields.
     * The same codec writes the entry back for the client sync, so both shapes round-trip.
     */
    public static Codec<ObjectiveEntry> entryCodec(QuestRegistry registry) {
        MapCodec<ObjectiveEntry> single = RecordCodecBuilder.<ObjectiveEntry>mapCodec(instance -> instance.group(
                Identifier.CODEC.<ObjectiveDefinition>dispatchMap("type", ObjectiveDefinition::typeId, id -> lookupObjective(registry, id))
                        .forGetter((ObjectiveEntry entry) -> entry.options().get(0)),
                Codec.BOOL.optionalFieldOf("optional", false).forGetter(ObjectiveEntry::optional)
        ).apply(instance, ObjectiveEntry::single));
        Codec<ObjectiveEntry> group = RecordCodecBuilder.<RawGroup>mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(raw -> ANY_OF),
                objectiveCodec(registry).listOf().fieldOf("options").forGetter(RawGroup::options),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(RawGroup::count),
                Codec.BOOL.optionalFieldOf("optional", false).forGetter(RawGroup::optional)
        ).apply(instance, (type, options, count, optional) -> new RawGroup(options, count, optional))).<ObjectiveEntry>flatXmap(raw -> {
            if (raw.options().size() < 2) {
                return DataResult.error(() -> "A questapi:any_of group needs at least 2 options");
            }
            if (raw.count() > raw.options().size()) {
                return DataResult.error(() -> "A questapi:any_of group has count " + raw.count()
                        + " but only " + raw.options().size() + " options");
            }
            return DataResult.success(new ObjectiveEntry(raw.options(), raw.count(), raw.optional()));
        }, entry -> DataResult.success(new RawGroup(entry.options(), entry.required(), entry.optional()))).codec();
        Codec<ObjectiveEntry> allOf = RecordCodecBuilder.<RawGroup>mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(raw -> ALL_OF),
                objectiveCodec(registry).listOf().fieldOf("options").forGetter(RawGroup::options),
                Codec.BOOL.optionalFieldOf("optional", false).forGetter(RawGroup::optional)
        ).apply(instance, (type, options, optional) -> new RawGroup(options, options.size(), optional))).<ObjectiveEntry>flatXmap(raw -> {
            if (raw.options().size() < 2) {
                return DataResult.error(() -> "A questapi:all_of group needs at least 2 options");
            }
            return DataResult.success(new ObjectiveEntry(raw.options(), raw.options().size(), raw.optional()));
        }, entry -> DataResult.success(new RawGroup(entry.options(), entry.required(), entry.optional()))).codec();
        Codec<ObjectiveEntry> singleCodec = single.codec();
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<ObjectiveEntry, T>> decode(DynamicOps<T> ops, T input) {
                String type = ops.getMap(input).result()
                        .map(map -> map.get("type"))
                        .flatMap(value -> ops.getStringValue(value).result())
                        .orElse("");
                if (type.equals(ANY_OF.toString())) {
                    return group.decode(ops, input);
                }
                if (type.equals(ALL_OF.toString())) {
                    return allOf.decode(ops, input);
                }
                return singleCodec.decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(ObjectiveEntry entry, DynamicOps<T> ops, T prefix) {
                // A group that needs every option is written as all_of, any other group as any_of.
                if (entry.isAllOf()) {
                    return allOf.encode(entry, ops, prefix);
                }
                return (entry.isGroup() ? group : singleCodec).encode(entry, ops, prefix);
            }
        };
    }

    private static MapCodec<? extends ObjectiveDefinition> lookupObjective(QuestRegistry registry, Identifier id) {
        if (id.equals(ANY_OF) || id.equals(ALL_OF)) {
            throw new IllegalArgumentException("A " + id + " group can't be placed inside another group");
        }
        ObjectiveType<?> type = registry.getObjectiveType(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown objective type: " + id));
        return type.codec();
    }

    public static Codec<QuestReward> rewardCodec(QuestRegistry registry) {
        return Identifier.CODEC.dispatch("type", QuestReward::typeId, id -> lookupReward(registry, id));
    }

    private static MapCodec<? extends QuestReward> lookupReward(QuestRegistry registry, Identifier id) {
        RewardType<?> type = registry.getRewardType(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reward type: " + id));
        return type.codec();
    }

    public static Codec<QuestCondition> conditionCodec(QuestRegistry registry) {
        return registry.conditionCodec();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(name)) {
                    return DataResult.success(constant);
                }
            }
            return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name);
        }, Enum::name);
    }

    public static Codec<QuestDisplay> displayCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestDisplay::title),
                ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(QuestDisplay::description),
                ItemStack.CODEC.fieldOf("icon").forGetter(QuestDisplay::icon),
                Codec.INT.optionalFieldOf("sort_order", 0).forGetter(QuestDisplay::sortOrder)
        ).apply(instance, QuestDisplay::new));
    }

    public static Codec<QuestRepeat> repeatCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                enumCodec(ResetMode.class).optionalFieldOf("reset_mode")
                        .forGetter(repeat -> Optional.ofNullable(repeat.resetMode())),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("reset_amount").forGetter(QuestRepeat::amount)
        ).apply(instance, (mode, amount) -> new QuestRepeat(mode.orElse(null), amount)));
    }

    public static Codec<QuestLifecycle> lifecycleCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("auto_activate", false).forGetter(QuestLifecycle::autoActivate),
                Codec.BOOL.optionalFieldOf("sequential", false).forGetter(QuestLifecycle::sequential),
                repeatCodec().optionalFieldOf("repeat").forGetter(QuestLifecycle::repeat),
                Codec.BOOL.optionalFieldOf("abandonable", false).forGetter(QuestLifecycle::abandonable)
        ).apply(instance, QuestLifecycle::new));
    }

    public static Codec<QuestTimeLimit> timeLimitCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(QuestTimeLimit::amount),
                enumCodec(TimeLimitUnit.class).fieldOf("unit").forGetter(QuestTimeLimit::unit)
        ).apply(instance, QuestTimeLimit::new));
    }

    public static Codec<FailTrigger> failTriggerCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("event").forGetter(FailTrigger::event),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("during_step").forGetter(FailTrigger::duringStep)
        ).apply(instance, FailTrigger::new));
    }

    public static Codec<QuestFailureRules> failureCodec(QuestRegistry registry) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("retryable", false).forGetter(QuestFailureRules::retryable),
                timeLimitCodec().optionalFieldOf("time_limit").forGetter(QuestFailureRules::timeLimit),
                ComponentSerialization.CODEC.optionalFieldOf("reason").forGetter(QuestFailureRules::reason),
                failTriggerCodec().listOf().optionalFieldOf("fail_on", List.of()).forGetter(QuestFailureRules::failOn),
                rewardCodec(registry).listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestFailureRules::rewards)
        ).apply(instance, QuestFailureRules::new));
    }

    public static Codec<RewardChoice> rewardChoiceCodec(QuestRegistry registry) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.validate(id -> RewardChoice.isValidId(id)
                        ? DataResult.success(id)
                        : DataResult.<String>error(() -> "Reward choice id '" + id + "' must use only a-z, 0-9 and _"))
                        .fieldOf("id").forGetter(RewardChoice::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(RewardChoice::label),
                rewardCodec(registry).listOf().validate(rewards -> rewards.isEmpty()
                        ? DataResult.<List<QuestReward>>error(() -> "A reward choice needs at least one reward")
                        : DataResult.success(rewards))
                        .fieldOf("rewards").forGetter(RewardChoice::rewards)
        ).apply(instance, RewardChoice::new));
    }

    public static Codec<QuestToastOverrides> toastOverridesCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.optionalFieldOf("started").forGetter(QuestToastOverrides::started),
                ComponentSerialization.CODEC.optionalFieldOf("ready").forGetter(QuestToastOverrides::ready),
                ComponentSerialization.CODEC.optionalFieldOf("completed").forGetter(QuestToastOverrides::completed),
                ComponentSerialization.CODEC.optionalFieldOf("failed").forGetter(QuestToastOverrides::failed)
        ).apply(instance, QuestToastOverrides::new));
    }

    /**
     * The quest codec is a thin composition of nested group codecs ({@code display},
     * {@code lifecycle}, {@code failure}, {@code toast_overrides}). A record codec holds at most 16
     * slots, so new quest fields belong inside the matching group, and a new top-level block is
     * only for a genuinely new concern.
     */
    public static Codec<Quest> questCodec(QuestRegistry registry) {
        QuestLifecycle defaultLifecycle = new QuestLifecycle(false, false, Optional.empty(), false);
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Quest::id),
                Identifier.CODEC.fieldOf("category").forGetter(Quest::categoryId),
                displayCodec().fieldOf("display").forGetter(Quest::display),
                lifecycleCodec().optionalFieldOf("lifecycle", defaultLifecycle).forGetter(Quest::lifecycle),
                entryCodec(registry).listOf().fieldOf("objectives").forGetter(Quest::objectiveEntries),
                rewardCodec(registry).listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
                conditionCodec(registry).listOf().optionalFieldOf("prerequisites", List.of()).forGetter(Quest::prerequisites),
                failureCodec(registry).optionalFieldOf("failure").forGetter(Quest::failure),
                toastOverridesCodec().optionalFieldOf("toast_overrides").forGetter(Quest::toastOverrides),
                Identifier.CODEC.optionalFieldOf("questline").forGetter(Quest::questline),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("chapter").forGetter(Quest::chapter),
                Codec.BOOL.optionalFieldOf("chapter_final", false).forGetter(Quest::chapterFinal),
                rewardChoiceCodec(registry).listOf().optionalFieldOf("reward_choices", List.of()).forGetter(Quest::rewardChoices)
        ).apply(instance, (id, category, display, lifecycle, objectives, rewards, prerequisites, failure, toastOverrides, questline, chapter, chapterFinal, rewardChoices) -> {
            Quest.Builder builder = Quest.builder(id)
                    .category(category)
                    .title(display.title())
                    .description(display.description())
                    .icon(display.icon())
                    .sortOrder(display.sortOrder())
                    .autoActivate(lifecycle.autoActivate())
                    .sequential(lifecycle.sequential())
                    .abandonable(lifecycle.abandonable())
                    .objectiveEntries(objectives)
                    .rewards(rewards)
                    .rewardChoices(rewardChoices)
                    .requires(prerequisites);
            lifecycle.repeat().ifPresent(repeat -> builder.repeatable(repeat.resetMode(), repeat.amount()));
            failure.ifPresent(builder::failure);
            toastOverrides.ifPresent(builder::toastOverrides);
            questline.ifPresent(builder::questline);
            chapter.ifPresent(builder::chapter);
            builder.chapterFinal(chapterFinal);
            return builder.build();
        }));
    }

    public static Codec<QuestlineDefinition> questlineCodec(QuestRegistry registry) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(QuestlineDefinition::id),
                Codec.STRING.optionalFieldOf("display_name", "").forGetter(QuestlineDefinition::displayName),
                Identifier.CODEC.optionalFieldOf("parent").forGetter(QuestlineDefinition::parent),
                conditionCodec(registry).listOf().optionalFieldOf("opens_when", List.of()).forGetter(QuestlineDefinition::opensWhen),
                conditionCodec(registry).listOf().optionalFieldOf("closes_when", List.of()).forGetter(QuestlineDefinition::closesWhen),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("chapter").forGetter(QuestlineDefinition::chapter)
        ).apply(instance, QuestlineDefinition::new));
    }
}
