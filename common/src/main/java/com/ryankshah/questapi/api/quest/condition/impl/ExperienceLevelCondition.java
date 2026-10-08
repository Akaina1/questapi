package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Requires the player to be at least a given experience level.
 */
public final class ExperienceLevelCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "experience_level");

    public static final MapCodec<ExperienceLevelCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(o -> o.level)
    ).apply(instance, ExperienceLevelCondition::new));

    public static final ConditionType<ExperienceLevelCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final int level;

    public ExperienceLevelCondition(int level) {
        this.level = level;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.experience_level", level);
    }

    @Override
    public boolean test(QuestContext context) {
        return context.player().experienceLevel >= level;
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.EXPERIENCE_LEVEL);
    }

    @Override
    public boolean live() {
        return true;
    }

    public int level() {
        return level;
    }
}
