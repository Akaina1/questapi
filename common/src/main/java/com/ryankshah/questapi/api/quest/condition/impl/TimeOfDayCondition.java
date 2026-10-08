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
 * Requires the player's current dimension to be in daytime or nighttime, per
 * {@link net.minecraft.world.level.Level#isBrightOutside()}/{@code isDarkOutside()} - always false
 * for a dimension with a fixed time (the Nether, the End), matching vanilla's own definition of "day".
 */
public final class TimeOfDayCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "time_of_day");

    public static final MapCodec<TimeOfDayCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("day").forGetter(o -> o.requiresDay)
    ).apply(instance, TimeOfDayCondition::new));

    public static final ConditionType<TimeOfDayCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final boolean requiresDay;

    public TimeOfDayCondition(boolean requiresDay) {
        this.requiresDay = requiresDay;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable(requiresDay ? "questapi.condition.time_of_day.day" : "questapi.condition.time_of_day.night");
    }

    @Override
    public boolean test(QuestContext context) {
        return requiresDay ? context.level().isBrightOutside() : context.level().isDarkOutside();
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.TIME_OF_DAY);
    }

    @Override
    public boolean live() {
        return true;
    }

    public boolean requiresDay() {
        return requiresDay;
    }
}
