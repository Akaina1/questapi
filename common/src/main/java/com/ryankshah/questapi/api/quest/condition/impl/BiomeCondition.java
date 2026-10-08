package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * Requires the player to currently be standing in a specific biome.
 */
public final class BiomeCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "biome");

    public static final MapCodec<BiomeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(o -> o.biome)
    ).apply(instance, BiomeCondition::new));

    public static final ConditionType<BiomeCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final ResourceKey<Biome> biome;

    public BiomeCondition(ResourceKey<Biome> biome) {
        this.biome = biome;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.biome", biome.identifier().toString());
    }

    @Override
    public boolean test(QuestContext context) {
        return context.level().getBiome(context.player().blockPosition()).is(biome);
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.POSITION);
    }

    @Override
    public boolean live() {
        return true;
    }

    public ResourceKey<Biome> biome() {
        return biome;
    }
}
