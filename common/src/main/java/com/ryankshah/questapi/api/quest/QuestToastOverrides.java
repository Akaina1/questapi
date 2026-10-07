package com.ryankshah.questapi.api.quest;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Per-quest replacements for the title line of the default toasts, so narrative quests can say
 * "You sided with Faction A" instead of "Quest completed". The toast body (the quest title) is
 * unchanged. Serialized as the {@code toast_overrides} block of the quest JSON.
 *
 * @param started   replaces "Quest accepted"
 * @param ready     replaces "Ready to turn in"
 * @param completed replaces the final "Quest completed" toast shown once rewards are claimed
 * @param failed    replaces "Quest failed"
 */
public record QuestToastOverrides(Optional<Component> started, Optional<Component> ready,
                                  Optional<Component> completed, Optional<Component> failed) {
}
