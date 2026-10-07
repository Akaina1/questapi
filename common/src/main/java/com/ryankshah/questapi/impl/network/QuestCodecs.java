package com.ryankshah.questapi.impl.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.FailTrigger;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestDisplay;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestLifecycle;
import com.ryankshah.questapi.api.quest.QuestRepeat;
import com.ryankshah.questapi.api.quest.QuestTimeLimit;
import com.ryankshah.questapi.api.quest.QuestToastOverrides;
import com.ryankshah.questapi.api.quest.ResetMode;
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

    private static MapCodec<? extends ObjectiveDefinition> lookupObjective(QuestRegistry registry, Identifier id) {
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
                repeatCodec().optionalFieldOf("repeat").forGetter(QuestLifecycle::repeat)
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
        QuestLifecycle defaultLifecycle = new QuestLifecycle(false, false, Optional.empty());
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Quest::id),
                Identifier.CODEC.fieldOf("category").forGetter(Quest::categoryId),
                displayCodec().fieldOf("display").forGetter(Quest::display),
                lifecycleCodec().optionalFieldOf("lifecycle", defaultLifecycle).forGetter(Quest::lifecycle),
                objectiveCodec(registry).listOf().fieldOf("objectives").forGetter(Quest::objectives),
                rewardCodec(registry).listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
                conditionCodec(registry).listOf().optionalFieldOf("prerequisites", List.of()).forGetter(Quest::prerequisites),
                failureCodec(registry).optionalFieldOf("failure").forGetter(Quest::failure),
                toastOverridesCodec().optionalFieldOf("toast_overrides").forGetter(Quest::toastOverrides)
        ).apply(instance, (id, category, display, lifecycle, objectives, rewards, prerequisites, failure, toastOverrides) -> {
            Quest.Builder builder = Quest.builder(id)
                    .category(category)
                    .title(display.title())
                    .description(display.description())
                    .icon(display.icon())
                    .sortOrder(display.sortOrder())
                    .autoActivate(lifecycle.autoActivate())
                    .sequential(lifecycle.sequential())
                    .objectives(objectives)
                    .rewards(rewards)
                    .requires(prerequisites);
            lifecycle.repeat().ifPresent(repeat -> builder.repeatable(repeat.resetMode(), repeat.amount()));
            failure.ifPresent(builder::failure);
            toastOverrides.ifPresent(builder::toastOverrides);
            return builder.build();
        }));
    }
}
