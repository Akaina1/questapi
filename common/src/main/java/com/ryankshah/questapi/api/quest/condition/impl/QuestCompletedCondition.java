package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Requires another quest to have been completed (rewards claimed) before this one unlocks. The
 * primary building block for quest chains/prerequisite trees.
 */
public final class QuestCompletedCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "quest_completed");

    public static final MapCodec<QuestCompletedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("quest").forGetter(o -> o.requiredQuestId)
    ).apply(instance, QuestCompletedCondition::new));

    public static final ConditionType<QuestCompletedCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final Identifier requiredQuestId;

    public QuestCompletedCondition(Identifier requiredQuestId) {
        this.requiredQuestId = requiredQuestId;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.quest_completed", requiredQuestId.toString());
    }

    @Override
    public boolean test(QuestContext context) {
        QuestState state = context.manager().getState(context.player(), requiredQuestId);
        return state == QuestState.REWARDED;
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.quest(requiredQuestId));
    }

    public Identifier requiredQuestId() {
        return requiredQuestId;
    }
}
