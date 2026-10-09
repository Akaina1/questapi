package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.reward.QuestReward;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One option of a quest's reward choice: when the quest is claimed the player picks exactly one of
 * the quest's choices, and its rewards are granted on top of the quest's fixed rewards.
 *
 * @param id      stable id of the choice, lowercase letters, digits and underscores only (it is saved with the
 *                player's progress and can be used in flag names)
 * @param label   short name shown in the quest book
 * @param rewards what the player receives for taking this choice
 */
public record RewardChoice(String id, Component label, List<QuestReward> rewards) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

    public RewardChoice {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Reward choice id '" + id + "' must use only a-z, 0-9 and _");
        }
        rewards = List.copyOf(rewards);
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("Reward choice '" + id + "' needs at least one reward");
        }
    }

    public static boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }
}
