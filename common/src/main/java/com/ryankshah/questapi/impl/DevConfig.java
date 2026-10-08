package com.ryankshah.questapi.impl;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.platform.Services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Smallest-sensible development configuration mechanism, shared by both loaders.
 * <p>
 * Reads (and, on first run, creates) {@code config/questapi.properties} with a {@code dev} flag and
 * a {@code repeatable-quest-default-reset-mode} setting. When {@code dev} is {@code true}, the
 * example quest tree and the {@code /quests} debug commands are registered; production quest
 * content registered by other mods is completely unaffected either way.
 */
public final class DevConfig {

    private static final String ALLOW_MANUAL_START_KEY = "allow-manual-start";
    private static final String ALLOW_MANUAL_ABANDON_KEY = "allow-manual-abandon";
    private static final String ALLOW_MANUAL_CLAIM_KEY = "allow-manual-claim";
    private static final String ALLOW_MANUAL_DELIVER_KEY = "allow-manual-deliver";
    private static final String SHOW_UNLOCKED_TOAST_KEY = "show-quest-unlocked-toast";
    private static final String SHOW_COMPLETED_TOAST_KEY = "show-quest-completed-toast";
    private static final String SHOW_STARTED_TOAST_KEY = "show-quest-started-toast";
    private static final String SHOW_REWARDED_TOAST_KEY = "show-quest-rewarded-toast";
    private static final String SHOW_OBJECTIVE_TOAST_KEY = "show-objective-completed-toast";
    private static final String SHOW_FAILED_TOAST_KEY = "show-quest-failed-toast";
    private static final String SHOW_CHAPTER_ENDING_TOAST_KEY = "show-chapter-ending-toast";
    private static final String AUTO_TRACK_STARTED_KEY = "auto-track-started-quests";
    private static final String TICKS_PER_GAME_DAY_KEY = "ticks-per-game-day";
    private static final int DEFAULT_TICKS_PER_GAME_DAY = 24000;

    private static boolean devMode = false;
    private static int ticksPerGameDay = DEFAULT_TICKS_PER_GAME_DAY;
    private static boolean showFailedToast = true;
    private static boolean showChapterEndingToast = true;
    private static boolean autoTrackStartedQuests = true;
    private static ResetMode defaultResetMode = ResetMode.WALL_CLOCK;
    private static boolean allowManualStart = true;
    private static boolean allowManualAbandon = true;
    private static boolean allowManualClaim = true;
    private static boolean allowManualDeliver = true;
    private static boolean showUnlockedToast = true;
    private static boolean showCompletedToast = true;
    private static boolean showStartedToast = true;
    private static boolean showRewardedToast = true;
    private static boolean showObjectiveCompletedToast = true;
    private static boolean loaded = false;

    private DevConfig() {
    }

    public static boolean isDevMode() {
        return devMode;
    }

    /**
     * Whether a player may start an {@code AVAILABLE} quest themselves, through the quest book.
     * When {@code false} the server ignores the start payload, so quests can only be started by
     * code calling {@code QuestManager#startQuest} (for example from an NPC dialog).
     */
    public static boolean allowManualStart() {
        return allowManualStart;
    }

    /**
     * Whether a player may abandon an {@code ACTIVE} quest themselves, through the quest book.
     */
    public static boolean allowManualAbandon() {
        return allowManualAbandon;
    }

    /**
     * Whether a player may claim the rewards of a {@code COMPLETED} quest themselves, through the
     * quest book.
     */
    public static boolean allowManualClaim() {
        return allowManualClaim;
    }

    /**
     * Whether a player may deliver items to a delivery objective themselves, through the quest book.
     */
    public static boolean allowManualDeliver() {
        return allowManualDeliver;
    }

    /**
     * Whether the server tells a player (toast and sound) when a quest becomes {@code AVAILABLE}.
     */
    public static boolean showUnlockedToast() {
        return showUnlockedToast;
    }

    /**
     * Whether the server tells a player (toast and sound) when a single objective is completed.
     * Partial progress never toasts.
     */
    public static boolean showObjectiveCompletedToast() {
        return showObjectiveCompletedToast;
    }

    /**
     * Whether the server tells a player (toast and sound) when a quest's rewards are claimed.
     */
    public static boolean showRewardedToast() {
        return showRewardedToast;
    }

    /**
     * Whether the server tells a player (toast and sound) when a quest's objectives are all done and
     * it is ready to turn in.
     */
    public static boolean showCompletedToast() {
        return showCompletedToast;
    }

    /**
     * Whether the server tells a player (toast and sound) when a quest becomes {@code ACTIVE}.
     */
    public static boolean showStartedToast() {
        return showStartedToast;
    }

    /**
     * Whether the server tells a player (toast and sound) when a quest fails.
     */
    public static boolean showFailedToast() {
        return showFailedToast;
    }

    /**
     * Whether the server warns a player (toast and sound) that handing in a chapter final quest
     * ends the chapter, when that quest becomes ready to turn in.
     */
    public static boolean showChapterEndingToast() {
        return showChapterEndingToast;
    }

    /**
     * Whether a quest is pinned to the player's HUD tracker automatically when it starts, as long
     * as the tracker has a free slot.
     */
    public static boolean autoTrackStartedQuests() {
        return autoTrackStartedQuests;
    }

    /**
     * How many ticks of the overworld day clock make one in-game day, used to convert
     * {@code GAME_DAYS} and {@code GAME_HOURS} quest time limits. Modpacks that change the length
     * of a day can override the vanilla 24000.
     */
    public static int ticksPerGameDay() {
        return ticksPerGameDay;
    }

    /**
     * The reset mode a repeatable quest resolves to when it does not specify its own via
     * {@code Quest.Builder#repeatable(ResetMode, int)}.
     */
    public static ResetMode defaultResetMode() {
        return defaultResetMode;
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = Services.PLATFORM.getConfigDirectory().resolve("questapi.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                QuestApi.LOG.warn("Failed to read questapi.properties, defaulting to dev=false", e);
            }
        } else {
            props.setProperty("dev", "false");
            props.setProperty("repeatable-quest-default-reset-mode", ResetMode.WALL_CLOCK.name());
            props.setProperty(ALLOW_MANUAL_START_KEY, "true");
            props.setProperty(ALLOW_MANUAL_ABANDON_KEY, "true");
            props.setProperty(ALLOW_MANUAL_CLAIM_KEY, "true");
            props.setProperty(ALLOW_MANUAL_DELIVER_KEY, "true");
            props.setProperty(SHOW_UNLOCKED_TOAST_KEY, "true");
            props.setProperty(SHOW_COMPLETED_TOAST_KEY, "true");
            props.setProperty(SHOW_STARTED_TOAST_KEY, "true");
            props.setProperty(SHOW_REWARDED_TOAST_KEY, "true");
            props.setProperty(SHOW_OBJECTIVE_TOAST_KEY, "true");
            props.setProperty(SHOW_FAILED_TOAST_KEY, "true");
            props.setProperty(SHOW_CHAPTER_ENDING_TOAST_KEY, "true");
            props.setProperty(AUTO_TRACK_STARTED_KEY, "true");
            props.setProperty(TICKS_PER_GAME_DAY_KEY, String.valueOf(DEFAULT_TICKS_PER_GAME_DAY));
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ignored) {
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "QuestAPI configuration.\n"
                        + "dev: set to true to register the example quest tree and /quests debug commands.\n"
                        + "repeatable-quest-default-reset-mode: WALL_CLOCK or IN_GAME_DAY, used by repeatable\n"
                        + "quests that don't specify their own reset mode.\n"
                        + "allow-manual-start/abandon/claim/deliver: set to false to make the server ignore the matching\n"
                        + "quest book action, so quests can only be driven by code (for example from NPC dialogs).\n"
                        + "show-quest-unlocked-toast / show-quest-started-toast / show-quest-completed-toast /\n"
                        + "show-quest-rewarded-toast: set to false to suppress the toast and sound when a quest becomes\n"
                        + "available / is accepted / has all objectives complete (ready to turn in) / has its rewards claimed.\n"
                        + "show-objective-completed-toast: set to false to suppress the toast when a single objective completes.\n"
                        + "show-quest-failed-toast: set to false to suppress the toast and sound when a quest fails.\n"
                        + "show-chapter-ending-toast: set to false to suppress the warning toast shown when a chapter final quest\n"
                        + "is ready to be turned in (handing it in ends the chapter).\n"
                        + "auto-track-started-quests: set to false to stop quests being pinned to the HUD tracker automatically\n"
                        + "when they start (players can still pin them from the quest book).\n"
                        + "ticks-per-game-day: length of an in-game day in ticks of the overworld day clock, used for\n"
                        + "GAME_DAYS and GAME_HOURS quest time limits (vanilla is 24000).");
            } catch (IOException e) {
                QuestApi.LOG.warn("Failed to write default questapi.properties", e);
            }
        }
        devMode = Boolean.parseBoolean(props.getProperty("dev", "false"));
        try {
            defaultResetMode = ResetMode.valueOf(props.getProperty("repeatable-quest-default-reset-mode", "WALL_CLOCK").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            QuestApi.LOG.warn("Invalid repeatable-quest-default-reset-mode in questapi.properties, defaulting to WALL_CLOCK", e);
            defaultResetMode = ResetMode.WALL_CLOCK;
        }
        allowManualStart = readBoolean(props, ALLOW_MANUAL_START_KEY);
        allowManualAbandon = readBoolean(props, ALLOW_MANUAL_ABANDON_KEY);
        allowManualClaim = readBoolean(props, ALLOW_MANUAL_CLAIM_KEY);
        allowManualDeliver = readBoolean(props, ALLOW_MANUAL_DELIVER_KEY);
        showUnlockedToast = readBoolean(props, SHOW_UNLOCKED_TOAST_KEY);
        showCompletedToast = readBoolean(props, SHOW_COMPLETED_TOAST_KEY);
        showStartedToast = readBoolean(props, SHOW_STARTED_TOAST_KEY);
        showRewardedToast = readBoolean(props, SHOW_REWARDED_TOAST_KEY);
        showObjectiveCompletedToast = readBoolean(props, SHOW_OBJECTIVE_TOAST_KEY);
        showFailedToast = readBoolean(props, SHOW_FAILED_TOAST_KEY);
        showChapterEndingToast = readBoolean(props, SHOW_CHAPTER_ENDING_TOAST_KEY);
        autoTrackStartedQuests = readBoolean(props, AUTO_TRACK_STARTED_KEY);
        ticksPerGameDay = readPositiveInt(props, TICKS_PER_GAME_DAY_KEY, DEFAULT_TICKS_PER_GAME_DAY);
        QuestApi.LOG.info("QuestAPI dev mode: {}, default repeatable-quest reset mode: {}", devMode, defaultResetMode);
        QuestApi.LOG.info("QuestAPI manual quest book actions allowed: start={}, abandon={}, claim={}, deliver={}",
                allowManualStart, allowManualAbandon, allowManualClaim, allowManualDeliver);
    }

    /**
     * Reads a boolean that defaults to {@code true}, so a config file written before the key
     * existed keeps the original behaviour.
     */
    private static boolean readBoolean(Properties props, String key) {
        return Boolean.parseBoolean(props.getProperty(key, "true").trim());
    }

    private static int readPositiveInt(Properties props, String key, int fallback) {
        try {
            int value = Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        QuestApi.LOG.warn("Invalid {} in questapi.properties, defaulting to {}", key, fallback);
        return fallback;
    }
}
