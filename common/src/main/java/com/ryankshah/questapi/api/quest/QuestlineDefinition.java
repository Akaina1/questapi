package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A group of quests that open and close together, such as the Raider story or a whole chapter.
 * Quests join a questline through {@link Quest#questline()}; a questline may itself sit inside a
 * {@code parent}, which is how chapters contain questlines.
 * <p>
 * Whether a questline is open is never stored: it is computed from the player's conditions whenever
 * a quest in it is asked about.
 * <ul>
 *     <li>It is <em>open</em> when every {@code opensWhen} condition passes and its parent (if any)
 *     is open.</li>
 *     <li>It is <em>closed</em> as soon as any {@code closesWhen} condition passes, or its parent is
 *     closed. Quests in a closed questline that were not started read
 *     {@link QuestState#PERMANENTLY_LOCKED}, and started ones are failed.</li>
 * </ul>
 *
 * @param displayName English name shown to the player, or empty to fall back to the id
 * @param chapter     the numbered chapter every quest in this questline belongs to unless it names
 *                    its own, or empty to inherit the chapter of the parent questline
 */
public record QuestlineDefinition(
        Identifier id,
        String displayName,
        Optional<Identifier> parent,
        List<QuestCondition> opensWhen,
        List<QuestCondition> closesWhen,
        Optional<Integer> chapter
) {

    public QuestlineDefinition {
        opensWhen = List.copyOf(opensWhen);
        closesWhen = List.copyOf(closesWhen);
    }
}
