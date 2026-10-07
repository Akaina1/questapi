package com.ryankshah.questapi.api.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The presentation group of a {@link Quest}: everything the player reads or sees in the quest
 * book. Serialized as the {@code display} block of the quest JSON.
 *
 * @param title       the quest's name
 * @param description flavour text shown in the quest detail pane
 * @param icon        the item drawn next to the quest
 * @param sortOrder   lower values are displayed first within a category
 */
public record QuestDisplay(Component title, Component description, ItemStack icon, int sortOrder) {
}
