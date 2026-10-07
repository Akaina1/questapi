package com.ryankshah.questapi.api.quest;

/**
 * Cooldown settings for a repeatable quest. Serialized as the {@code repeat} block inside the
 * {@code lifecycle} block of the quest JSON; a quest is repeatable exactly when this is present.
 *
 * @param resetMode how the cooldown is measured, or {@code null} to use the server's configured
 *                  default (see {@code questapi.properties})
 * @param amount    the cooldown length: hours if the resolved mode is {@link ResetMode#WALL_CLOCK},
 *                  in-game days if it is {@link ResetMode#IN_GAME_DAY}; always positive
 */
public record QuestRepeat(ResetMode resetMode, int amount) {
}
