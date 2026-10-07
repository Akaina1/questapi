package com.ryankshah.questapi.api.quest;

/**
 * A deadline for an active quest. Serialized as the {@code time_limit} block inside the
 * {@code failure} block of the quest JSON.
 *
 * @param amount how many {@code unit}s the player has; always positive
 * @param unit   the clock the deadline is measured on
 */
public record QuestTimeLimit(int amount, TimeLimitUnit unit) {
}
