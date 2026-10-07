package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Requires another quest to have failed before this one unlocks, for branching quest lines. Only a
 * quest that is not retryable stays {@code FAILED}; a retryable quest is reset straight after it
 * fails, so it cannot satisfy this condition for long.
 */
public final class QuestFailedCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "quest_failed");

    public static final MapCodec<QuestFailedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("quest").forGetter(o -> o.requiredQuestId)
    ).apply(instance, QuestFailedCondition::new));

    public static final ConditionType<QuestFailedCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final Identifier requiredQuestId;

    public QuestFailedCondition(Identifier requiredQuestId) {
        this.requiredQuestId = requiredQuestId;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.quest_failed", requiredQuestId.toString());
    }

    @Override
    public boolean test(QuestContext context) {
        return context.manager().getState(context.player(), requiredQuestId) == QuestState.FAILED;
    }

    public Identifier requiredQuestId() {
        return requiredQuestId;
    }
}
