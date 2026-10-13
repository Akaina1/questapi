package com.ryankshah.questapi.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * An authored place a quest step is about, written as {@code "location"} on an objective entry:
 * {@code "location": {"dimension": "minecraft:overworld", "pos": [14, 67, 3]}}. The quest API only
 * stores it; what to do with it (a compass, a map marker, a path) is up to the mod that reads it. It is
 * a hint, not a rule: nothing checks that the player goes there.
 *
 * @param dimension the dimension the place is in; the overworld when left out
 * @param pos       the block position of the place
 */
public record ObjectiveLocation(Identifier dimension, BlockPos pos) {
    public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    public static final Codec<ObjectiveLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("dimension", OVERWORLD).forGetter(ObjectiveLocation::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(ObjectiveLocation::pos)
    ).apply(instance, ObjectiveLocation::new));
}
