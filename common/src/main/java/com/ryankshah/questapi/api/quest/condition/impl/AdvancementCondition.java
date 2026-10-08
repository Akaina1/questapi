package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Requires the player to have completed a specific vanilla or datapack advancement.
 */
public final class AdvancementCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "advancement");

    public static final MapCodec<AdvancementCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("advancement").forGetter(o -> o.advancementId)
    ).apply(instance, AdvancementCondition::new));

    public static final ConditionType<AdvancementCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final Identifier advancementId;

    public AdvancementCondition(Identifier advancementId) {
        this.advancementId = advancementId;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.advancement", advancementId.toString());
    }

    @Override
    public boolean test(QuestContext context) {
        AdvancementHolder advancement = context.server().getAdvancements().get(advancementId);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = context.player().getAdvancements().getOrStartProgress(advancement);
        return progress.isDone();
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.ADVANCEMENT);
    }

    public Identifier advancementId() {
        return advancementId;
    }
}
