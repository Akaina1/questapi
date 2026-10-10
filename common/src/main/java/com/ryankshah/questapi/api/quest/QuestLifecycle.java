package com.ryankshah.questapi.api.quest;

import java.util.Optional;

/**
 * The behaviour group of a {@link Quest}: how it is started, how its objectives are ordered,
 * whether it can be repeated and whether the player may drop it. Serialized as the
 * {@code lifecycle} block of the quest JSON.
 *
 * @param autoActivate skips the {@code AVAILABLE} state and becomes {@code ACTIVE} as soon as the
 *                     prerequisites are met
 * @param sequential   objectives must be completed in order
 * @param repeat       cooldown settings; empty for a quest that cannot be repeated
 * @param abandonable  the player may abandon the quest from the quest book (the book's Abandon
 *                     button also needs the {@code allow-manual-abandon} setting)
 * @param hideWhenRewarded the quest book does not list the quest in its Completed tab once it is
 *                     rewarded (for repeatable quests that would otherwise fill the tab)
 */
public record QuestLifecycle(boolean autoActivate, boolean sequential, Optional<QuestRepeat> repeat, boolean abandonable,
        boolean hideWhenRewarded) {
}
