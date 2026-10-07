package com.ryankshah.questapi.api.quest;

/**
 * The clock a {@link QuestTimeLimit} is measured on.
 */
public enum TimeLimitUnit {
    /**
     * In-game days of the overworld day/night clock. Sleeping skips the clock forward, so skipped
     * time counts against the deadline.
     */
    GAME_DAYS,

    /**
     * A 24th of an in-game day, on the same clock as {@link #GAME_DAYS}.
     */
    GAME_HOURS,

    /**
     * Real seconds, counted only while the player is online and the quest is active.
     */
    REAL_SECONDS;

    /**
     * Whether this unit follows the overworld day/night clock rather than server ticks.
     */
    public boolean usesDayClock() {
        return this != REAL_SECONDS;
    }

    /**
     * Converts {@code amount} of this unit into ticks.
     *
     * @param ticksPerGameDay the configured length of an in-game day in ticks
     */
    public long toTicks(int amount, int ticksPerGameDay) {
        return switch (this) {
            case GAME_DAYS -> (long) amount * ticksPerGameDay;
            case GAME_HOURS -> (long) amount * ticksPerGameDay / 24L;
            case REAL_SECONDS -> (long) amount * 20L;
        };
    }
}
