package com.ryankshah.questapi.api.quest;

import java.util.Optional;

/**
 * The behaviour group of a {@link Quest}: how it is started, how its objectives are ordered, and
 * whether it can be repeated. Serialized as the {@code lifecycle} block of the quest JSON.
 *
 * @param autoActivate skips the {@code AVAILABLE} state and becomes {@code ACTIVE} as soon as the
 *                     prerequisites are met
 * @param sequential   objectives must be completed in order
 * @param repeat       cooldown settings; empty for a quest that cannot be repeated
 */
public record QuestLifecycle(boolean autoActivate, boolean sequential, Optional<QuestRepeat> repeat) {
}
