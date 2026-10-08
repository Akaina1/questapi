package com.ryankshah.questapi.api.quest.condition;

import net.minecraft.resources.Identifier;

/**
 * Keys naming the things a {@link QuestCondition} depends on. A condition lists its keys through
 * {@link QuestCondition#triggers()}; when something happens that could change the answer, the
 * matching key is passed to {@code QuestManager#notifyTrigger} and only the quests that mention that
 * key are re-evaluated. This is what keeps availability checks independent of the total quest count.
 */
public final class TriggerKeys {

    /** The player moved to a different block. */
    public static final Identifier POSITION = Identifier.fromNamespaceAndPath("questapi", "position");
    /** The player's inventory contents changed. */
    public static final Identifier INVENTORY = Identifier.fromNamespaceAndPath("questapi", "inventory");
    /** Day turned to night or night to day in the overworld. */
    public static final Identifier TIME_OF_DAY = Identifier.fromNamespaceAndPath("questapi", "time_of_day");
    /** The weather in the overworld changed. */
    public static final Identifier WEATHER = Identifier.fromNamespaceAndPath("questapi", "weather");
    /** The player's vanilla experience level changed. */
    public static final Identifier EXPERIENCE_LEVEL = Identifier.fromNamespaceAndPath("questapi", "experience_level");
    /** The player earned an advancement. */
    public static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath("questapi", "advancement");

    private TriggerKeys() {
    }

    /**
     * The key fired when the state of {@code questId} changes (started, completed, rewarded, failed
     * or reset) for a player.
     */
    public static Identifier quest(Identifier questId) {
        return Identifier.fromNamespaceAndPath(questId.getNamespace(), "quest/" + questId.getPath());
    }

    /**
     * The key fired when {@code flagId} is set or cleared for a player.
     */
    public static Identifier flag(Identifier flagId) {
        return Identifier.fromNamespaceAndPath(flagId.getNamespace(), "flag/" + flagId.getPath());
    }
}
