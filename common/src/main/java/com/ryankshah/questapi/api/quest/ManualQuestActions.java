package com.ryankshah.questapi.api.quest;

/**
 * Which player-initiated actions the server allows from the quest book. Configured through
 * {@code config/questapi.properties} and synced to clients so the default GUI can hide buttons that
 * the server would reject anyway.
 * <p>
 * There is no claim action: rewards are never claimed from the book, only by the mod that owns the
 * turn-in through {@code QuestManager#claimRewards}.
 *
 * @param start   the player may start an {@code AVAILABLE} quest from the book
 * @param abandon the player may abandon an {@code ACTIVE} quest from the book
 * @param deliver the player may hand in items for a delivery objective from the book
 */
public record ManualQuestActions(boolean start, boolean abandon, boolean deliver) {

    public static final ManualQuestActions ALL = new ManualQuestActions(true, true, true);
}
