package com.ryankshah.questapi.client;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Client side extension points of the quest book that the quest API itself has no behaviour for. A mod
 * registers its implementation once during client setup; without one, the matching button of the book
 * is greyed out.
 */
public final class ClientQuestHooks {

    /**
     * Lets the quest book switch a quest's path (a trail, an arrow, a map line: whatever the mod that
     * registers this draws to lead the player to the quest's next place) on and off, without untracking
     * the quest. The quest API keeps no state for it: the book only asks and tells.
     */
    public interface PathControl {

        /**
         * Whether the path of this quest is currently shown.
         */
        boolean isPathShown(Identifier questId);

        /**
         * Shows or hides the path of this quest, from the "Show Path" / "Hide Path" button.
         */
        void setPathShown(Identifier questId, boolean shown);
    }

    private static PathControl pathControl;

    private ClientQuestHooks() {
    }

    /**
     * Registers the handler of the quest book's path button, replacing an earlier one.
     */
    public static void setPathControl(PathControl control) {
        pathControl = control;
    }

    /**
     * The registered path handler, if any mod registered one.
     */
    public static Optional<PathControl> pathControl() {
        return Optional.ofNullable(pathControl);
    }
}
