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

    private static boolean devMode = false;
    private static ResetMode defaultResetMode = ResetMode.WALL_CLOCK;
    private static boolean allowManualStart = true;
    private static boolean allowManualAbandon = true;
    private static boolean allowManualClaim = true;
    private static boolean allowManualDeliver = true;
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
                        + "quest book action, so quests can only be driven by code (for example from NPC dialogs).");
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
}
