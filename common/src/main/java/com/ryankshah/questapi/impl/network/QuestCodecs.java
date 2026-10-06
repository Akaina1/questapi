package com.ryankshah.questapi.impl.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveType;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import com.ryankshah.questapi.api.quest.reward.RewardType;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

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

    public static Codec<Quest> questCodec(QuestRegistry registry) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Quest::id),
                ComponentSerialization.CODEC.fieldOf("title").forGetter(Quest::title),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Quest::description),
                ItemStack.CODEC.fieldOf("icon").forGetter(Quest::icon),
                Identifier.CODEC.fieldOf("category").forGetter(Quest::categoryId),
                objectiveCodec(registry).listOf().fieldOf("objectives").forGetter(Quest::objectives),
                rewardCodec(registry).listOf().fieldOf("rewards").forGetter(Quest::rewards),
                conditionCodec(registry).listOf().fieldOf("prerequisites").forGetter(Quest::prerequisites),
                Codec.BOOL.fieldOf("auto_activate").forGetter(Quest::autoActivate),
                Codec.INT.fieldOf("sort_order").forGetter(Quest::sortOrder),
                Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(Quest::repeatable),
                Codec.STRING.xmap(ResetMode::valueOf, Enum::name).optionalFieldOf("reset_mode")
                        .forGetter(q -> Optional.ofNullable(q.resetMode())),
                Codec.INT.optionalFieldOf("reset_amount", 0).forGetter(Quest::resetAmount),
                Codec.BOOL.optionalFieldOf("sequential", false).forGetter(Quest::sequential)
        ).apply(instance, (id, title, description, icon, category, objectives, rewards, prerequisites, autoActivate, sortOrder, repeatable, resetMode, resetAmount, sequential) -> {
            Quest.Builder builder = Quest.builder(id)
                    .title(title)
                    .description(description)
                    .icon(icon)
                    .category(category)
                    .objectives(objectives)
                    .rewards(rewards)
                    .requires(prerequisites)
                    .autoActivate(autoActivate)
                    .sortOrder(sortOrder)
                    .sequential(sequential);
            if (repeatable) {
                builder.repeatable(resetMode.orElse(null), resetAmount);
            }
            return builder.build();
        }));
    }
}
