package com.ryankshah.questapi.api.quest.objective;

import net.minecraft.resources.Identifier;

/**
 * Well-known event keys used to drive push-based {@link ObjectiveDefinition#evaluate} calls.
 * <p>
 * Poll-based objectives (item possession, mined/crafted/killed stats, exploration) ignore the event
 * key entirely and recompute their progress from scratch every time they are evaluated. Push-based
 * objectives (block placement, taming) only care about their specific event key and otherwise return
 * the progress unchanged.
 * <p>
 * Third-party mods may define and dispatch their own event keys for custom objective types via
 * {@link com.ryankshah.questapi.api.QuestManager#pushObjectiveEvent}.
 */
public final class ObjectiveEventKeys {

    /** Fired periodically (roughly once per second) for every player with active quests. */
    public static final Identifier TICK = Identifier.fromNamespaceAndPath("questapi", "tick");

    /** Fired when a player places a block. */
    public static final Identifier BLOCK_PLACED = Identifier.fromNamespaceAndPath("questapi", "block_placed");

    /** Fired when a player tames an animal. */
    public static final Identifier ENTITY_TAMED = Identifier.fromNamespaceAndPath("questapi", "entity_tamed");

    /** Fired when a player delivers items to a quest via the GUI or a command. */
    public static final Identifier ITEM_DELIVERED = Identifier.fromNamespaceAndPath("questapi", "item_delivered");

    /**
     * Fired when a player dies. No built-in objective listens for it; quests use it in
     * {@code failure.fail_on} to fail when the player dies.
     */
    public static final Identifier PLAYER_DIED = Identifier.fromNamespaceAndPath("questapi", "player_died");

    private ObjectiveEventKeys() {
    }
}
