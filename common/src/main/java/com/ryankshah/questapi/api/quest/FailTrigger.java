package com.ryankshah.questapi.api.quest;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * An objective event that fails an active quest, e.g. {@code questapi:player_died}. Event keys are
 * the same keys objectives match (see {@code ObjectiveEventKeys}).
 *
 * @param event      the event key that triggers the failure
 * @param duringStep if present, the event only fails the quest while the objective at this
 *                   0-based index is unlocked and incomplete; otherwise it fails the quest at any
 *                   point
 */
public record FailTrigger(Identifier event, Optional<Integer> duringStep) {
}
