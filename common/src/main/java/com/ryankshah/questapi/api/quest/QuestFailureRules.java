package com.ryankshah.questapi.api.quest;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * How a quest can fail and what happens afterwards. Serialized as the {@code failure} block of the
 * quest JSON; a quest without this block can only fail through {@code QuestManager#failQuest}.
 *
 * @param retryable if {@code true}, a failed quest is reset straight back to {@code AVAILABLE} (or
 *                  {@code ACTIVE} for auto-activating quests) with fresh objectives; if
 *                  {@code false}, it stays {@code FAILED} permanently, which makes it usable as a
 *                  {@code questapi:quest_failed} prerequisite for branching quest lines
 * @param timeLimit optional deadline
 * @param reason    optional line shown in the quest detail pane while the quest is failed
 * @param failOn    events that fail the quest while it is active
 */
public record QuestFailureRules(boolean retryable, Optional<QuestTimeLimit> timeLimit, Optional<Component> reason,
                                List<FailTrigger> failOn) {
}
