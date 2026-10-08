package com.ryankshah.questapi.api.quest.condition.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryankshah.questapi.api.quest.QuestContext;
import com.ryankshah.questapi.api.quest.condition.ConditionType;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.condition.TriggerKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Requires the player to currently possess at least {@code amount} of a given item, e.g. a quest
 * that only unlocks once the player owns a specific key item.
 */
public final class ItemPossessionCondition implements QuestCondition {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("questapi", "item_possession");

    public static final MapCodec<ItemPossessionCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(o -> o.item),
            Codec.INT.fieldOf("amount").forGetter(o -> o.amount)
    ).apply(instance, ItemPossessionCondition::new));

    public static final ConditionType<ItemPossessionCondition> TYPE = new ConditionType<>(TYPE_ID, CODEC);

    private final Item item;
    private final int amount;

    public ItemPossessionCondition(Item item, int amount) {
        this.item = item;
        this.amount = amount;
    }

    @Override
    public Identifier typeId() {
        return TYPE_ID;
    }

    @Override
    public Component describe() {
        return Component.translatable("questapi.condition.item_possession", amount, new ItemStack(item).getHoverName());
    }

    @Override
    public boolean test(QuestContext context) {
        return context.player().getInventory().countItem(item) >= amount;
    }

    @Override
    public List<Identifier> triggers() {
        return List.of(TriggerKeys.INVENTORY);
    }

    @Override
    public boolean live() {
        return true;
    }
}
