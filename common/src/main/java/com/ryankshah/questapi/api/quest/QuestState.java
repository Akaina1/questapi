package com.ryankshah.questapi.api.quest;

/**
 * The lifecycle state of a single quest for a single player.
 * <pre>
 * LOCKED -----------> AVAILABLE -----------> ACTIVE -----------> COMPLETED -----------> REWARDED
 *   ^  prerequisites       |  start()            |  objectives         |  claimReward()
 *   |  not met yet         |  (auto or manual)    |  all met            |
 *   |                      |                      |                     |
 *   +----------------------+----------------------+---------------------+
 *                       resetQuest() returns a quest to LOCKED or AVAILABLE
 * </pre>
 * <ul>
 *     <li>{@link #LOCKED} - one or more {@link com.ryankshah.questapi.api.quest.condition.QuestCondition}
 *     prerequisites are not yet satisfied. Hidden or greyed out in the GUI depending on configuration.</li>
 *     <li>{@link #AVAILABLE} - prerequisites are satisfied but the player has not started the quest.
 *     Quests marked {@code autoActivate} skip this state entirely and jump straight to {@link #ACTIVE}.</li>
 *     <li>{@link #ACTIVE} - the quest has been started and its objectives are being tracked.</li>
 *     <li>{@link #COMPLETED} - every objective has reached its target amount. Rewards have not yet
 *     been granted.</li>
 *     <li>{@link #REWARDED} - the player has claimed the quest's rewards. Terminal state; a quest
 *     only leaves it via an explicit reset.</li>
 *     <li>{@link #ABANDONED} - the player cancelled an in-progress quest. Functionally equivalent to
 *     {@link #AVAILABLE} (or {@link #LOCKED} if prerequisites regressed) but preserved as a distinct
 *     state for UI/analytics purposes.</li>
 *     <li>{@link #FAILED} - the quest was failed by code calling {@code QuestManager#failQuest}
 *     (for example a timer running out or a quest giver dying). Objective progress is kept for
 *     display. The quest only leaves this state via an explicit reset. Failure rewards, if the
 *     quest defines any, are claimed while it stays in this state.</li>
 *     <li>{@link #PERMANENTLY_LOCKED} - the quest's questline (or one of its parent questlines) has
 *     been closed, so the quest can never be started. Like {@link #LOCKED} and
 *     {@link #AVAILABLE} this is computed on demand and never stored.</li>
 * </ul>
 * Only quests the player has started are stored: {@link #ACTIVE}, {@link #COMPLETED},
 * {@link #REWARDED} and {@link #FAILED}. {@link #LOCKED}, {@link #AVAILABLE} and
 * {@link #PERMANENTLY_LOCKED} are derived from the player's current conditions whenever they are
 * asked for, and {@link #ABANDONED} is no longer produced (an abandoned quest simply returns to its
 * computed state).
 * New values are only ever appended, so anything that stores or sends the ordinal stays valid.
 */
public enum QuestState {
    LOCKED,
    AVAILABLE,
    ACTIVE,
    COMPLETED,
    REWARDED,
    ABANDONED,
    FAILED,
    PERMANENTLY_LOCKED;

    /**
     * Whether this state is saved with the player's progress, as opposed to being computed from
     * their current conditions.
     */
    public boolean stored() {
        return this == ACTIVE || this == COMPLETED || this == REWARDED || this == FAILED;
    }
}
