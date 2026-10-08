package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Passes if <em>any</em> of its wrapped conditions pass, unlike a quest's top-level prerequisite
 * list which requires <em>all</em> of them. The building block for "unlock if you've done X or Y"
 * requirements.
 * <p>
 * Unlike every other built-in condition, this one wraps other {@link QuestCondition}s and so needs
 * the registry's dispatch codec to (de)serialize them - see {@link #type(QuestRegistry)}. A
 * third-party mod writing its own composite condition should follow the same shape: a static
 * factory taking {@link QuestRegistry} instead of a bare {@code TYPE} constant, registered as
 * {@code registry.registerConditionType(MyComposite.type(registry))}.
 */
public final class AnyOfCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "any_of");

    private final List<QuestCondition> conditions;

    public AnyOfCondition(List<QuestCondition> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    public static MapCodec<AnyOfCondition> codec(QuestRegistry registry) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                registry.conditionCodec().listOf().fieldOf("conditions").forGetter(o -> o.conditions)
        ).apply(instance, AnyOfCondition::new));
    }

    public static ConditionType<AnyOfCondition> type(QuestRegistry registry) {
        return new ConditionType<>(TYPE_ID, codec(registry));
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        Component joined = Component.empty();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                joined = joined.copy().append(Component.translatable("questapi.condition.any_of.separator"));
            }
            joined = joined.copy().append(conditions.get(i).describe());
        }
        return Component.translatable("questapi.condition.any_of", joined);
    }

    @Override
    public boolean test(QuestContext context) {
        for (QuestCondition condition : conditions) {
            if (condition.test(context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Identifier> triggers() {
        List<Identifier> keys = new ArrayList<>();
        for (QuestCondition condition : conditions) {
            for (Identifier key : condition.triggers()) {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    @Override
    public boolean live() {
        for (QuestCondition condition : conditions) {
            if (condition.live()) {
                return true;
            }
        }
        return false;
    }

    public List<QuestCondition> conditions() {
        return conditions;
    }
}
