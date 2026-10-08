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
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Requires the player's current dimension to be experiencing a specific kind of weather.
 */
public final class WeatherCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "weather");

    public static final MapCodec<WeatherCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(WeatherType::valueOf, Enum::name).fieldOf("weather").forGetter(o -> o.weather)
    ).apply(instance, WeatherCondition::new));

    public static final ConditionType<WeatherCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final WeatherType weather;

    public WeatherCondition(WeatherType weather) {
        this.weather = weather;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.weather." + weather.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean test(QuestContext context) {
        Level level = context.level();
        return switch (weather) {
            case CLEAR -> !level.isRaining();
            case RAIN -> level.isRaining() && !level.isThundering();
            case THUNDER -> level.isThundering();
        };
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.WEATHER);
    }

    @Override
    public boolean live() {
        return true;
    }

    public WeatherType weather() {
        return weather;
    }

    public enum WeatherType {
        CLEAR, RAIN, THUNDER
    }
}
