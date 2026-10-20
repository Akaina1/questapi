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
 * @param showUnlocked shows the "New quest available" toast for this quest even when the global
 *                     {@code show-quest-unlocked-toast} switch is off; for a quest that starts itself
 *                     ({@code auto_activate}) the "Quest accepted" toast is skipped, so one toast shows
 */
public record QuestToastOverrides(Optional<Component> started, Optional<Component> ready,
                                  Optional<Component> completed, Optional<Component> failed,
                                  boolean showUnlocked) {
}
