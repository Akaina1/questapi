package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.QuestTimeLimit;
import net.minecraft.network.chat.Component;

/**
 * Shared text helpers for the default GUI. Kept separate from rendering code so translation keys
 * live in one obvious place.
 */
public final class QuestGuiText {

    private QuestGuiText() {
    }

    public static Component stateLabel(Quest quest, QuestState state) {
        return switch (state) {
            case LOCKED -> Component.translatable("questapi.gui.state.locked");
            case AVAILABLE -> Component.translatable("questapi.gui.state.available");
            case ACTIVE -> Component.translatable("questapi.gui.state.active");
            case COMPLETED -> Component.translatable("questapi.gui.state.completed");
            case REWARDED -> quest.repeatable()
                    ? Component.translatable("questapi.gui.state.rewarded.repeatable")
                    : Component.translatable("questapi.gui.state.rewarded");
            case ABANDONED -> Component.translatable("questapi.gui.state.abandoned");
            case FAILED -> Component.translatable("questapi.gui.state.failed");
            case PERMANENTLY_LOCKED -> Component.translatable("questapi.gui.state.permanently_locked");
        };
    }

    /**
     * The quest's "[Lv N]" tag, coloured by how its level compares to the player's level (the
     * vanilla experience level, so a mod that mirrors its own level onto the XP bar gets matching
     * colours): grey at 6 or more below, white up to 2 above, yellow up to 5 above, red beyond.
     * {@code null} if the quest has no level.
     */
    public static Component levelTag(Quest quest) {
        if (quest.level().isEmpty()) {
            return null;
        }
        int level = quest.level().get();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        int difference = level - (player == null ? level : player.experienceLevel);
        int color = difference <= -6 ? 0xAAAAAA : difference <= 2 ? 0xFFFFFF : difference <= 5 ? 0xFFFF55 : 0xFF5555;
        return Component.translatable("questapi.gui.level_tag", level).withStyle(style -> style.withColor(color));
    }

    /**
     * "Time limit: 5 day(s)" for a quest with a time limit, or {@code null} if it has none.
     */
    public static Component timeLimitLabel(Quest quest) {
        QuestTimeLimit limit = quest.failure().flatMap(QuestFailureRules::timeLimit).orElse(null);
        if (limit == null) {
            return null;
        }
        Component amount = switch (limit.unit()) {
            case GAME_DAYS -> Component.translatable("questapi.gui.time.limit.game_days", limit.amount());
            case GAME_HOURS -> Component.translatable("questapi.gui.time.limit.game_hours", limit.amount());
            case REAL_SECONDS -> Component.literal(formatMinutesSeconds(limit.amount() * 20L));
        };
        return Component.translatable("questapi.gui.time_limit", amount);
    }

    /**
     * "Time left: 2d 5h" (or "mm:ss" for real-time limits) for the given remaining ticks of the
     * quest's own clock.
     */
    public static Component timeRemainingLabel(Quest quest, long remainingTicks, int ticksPerGameDay) {
        QuestTimeLimit limit = quest.failure().flatMap(QuestFailureRules::timeLimit).orElse(null);
        Component remaining;
        if (limit != null && limit.unit().usesDayClock()) {
            long hours = (remainingTicks * 24L + ticksPerGameDay - 1L) / ticksPerGameDay;
            remaining = hours >= 24L
                    ? Component.translatable("questapi.gui.time.days_hours", hours / 24L, hours % 24L)
                    : Component.translatable("questapi.gui.time.hours", hours);
        } else {
            remaining = Component.literal(formatMinutesSeconds(remainingTicks));
        }
        return Component.translatable("questapi.gui.time_remaining", remaining);
    }

    public static Component retryLabel(QuestFailureRules rules) {
        return Component.translatable(rules.retryable() ? "questapi.gui.retry.yes" : "questapi.gui.retry.no");
    }

    private static String formatMinutesSeconds(long ticks) {
        long totalSeconds = ticks / 20L;
        return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    /**
     * Consistent colour for a quest's state, used by both the quest list rows and the detail panel.
     */
    public static int stateColor(QuestState state) {
        return switch (state) {
            case LOCKED, ABANDONED, PERMANENTLY_LOCKED -> 0xFF808080;
            case AVAILABLE -> 0xFFFFFFFF;
            case ACTIVE -> 0xFFFFD83C;
            case COMPLETED -> 0xFFFFAA00;
            case REWARDED -> 0xFF55C4FF;
            case FAILED -> 0xFFFF5555;
        };
    }
}
