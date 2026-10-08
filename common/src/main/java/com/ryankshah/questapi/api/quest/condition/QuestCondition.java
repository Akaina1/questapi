package com.ryankshah.questapi.api.quest.condition;

import com.ryankshah.questapi.api.quest.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A predicate a player must satisfy for a quest to unlock (become {@code AVAILABLE} instead of
 * {@code LOCKED}). Conditions are evaluated server-side whenever something asks for a quest's state,
 * and again when one of the {@link #triggers()} they declare fires, which is what drives the
 * unlock toast and auto-activation.
 * <p>
 * A quest's full set of conditions must <em>all</em> pass for it to unlock; this includes the
 * implicit "other quest completed" prerequisites as well as any additional conditions such as
 * advancement or item requirements.
 */
public interface QuestCondition {

    Identifier typeId();

    /**
     * Short human-readable description shown in the GUI while the quest is locked,
     * e.g. "Requires: Stone Age".
     */
    Component describe();

    boolean test(QuestContext context);

    /**
     * The {@link TriggerKeys keys} whose firing can change this condition's answer. A quest is
     * re-evaluated for its unlock toast and auto-activation only when one of these fires (and once at
     * login), so a condition that returns nothing is only checked then and whenever something asks
     * for the quest's state.
     */
    default List<Identifier> triggers() {
        return List.of();
    }

    /**
     * Whether the answer depends on the live state of the world or the player (time, position,
     * inventory) rather than on stored history such as flags and quest states. Live conditions are
     * checked after the cheap stored ones, so a quest whose flags are not met never reaches them.
     */
    default boolean live() {
        return false;
    }
}
