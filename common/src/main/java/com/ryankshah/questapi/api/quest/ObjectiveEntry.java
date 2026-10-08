package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;

import java.util.List;

/**
 * One line of a quest's objective list as the quest author wrote it: either a single objective or a
 * group of options of which {@code required} must be completed ("choose one", "any two of").
 * <p>
 * Progress is still saved per objective, by the position in the flat {@link Quest#objectives()}
 * list, where the options of a group are numbered one after another. An entry only adds the rules
 * on top of that list: whether it is optional, and how many options a group needs.
 *
 * @param options  the objectives of this entry; one for a single objective, two or more for a group
 * @param required how many options must be complete (always 1 for a single objective)
 * @param optional {@code true} if the quest can complete without this entry being done
 */
public record ObjectiveEntry(List<ObjectiveDefinition> options, int required, boolean optional) {

    public ObjectiveEntry {
        options = List.copyOf(options);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("An objective entry needs at least one objective");
        }
        if (required < 1 || required > options.size()) {
            throw new IllegalArgumentException("An objective entry needs between 1 and " + options.size()
                    + " objectives to be completed, got " + required);
        }
    }

    public static ObjectiveEntry single(ObjectiveDefinition objective, boolean optional) {
        return new ObjectiveEntry(List.of(objective), 1, optional);
    }

    /**
     * Whether this entry is a choice between several objectives.
     */
    public boolean isGroup() {
        return options.size() > 1;
    }

    /**
     * Whether this entry is a group in which every option must be completed.
     */
    public boolean isAllOf() {
        return isGroup() && required == options.size();
    }
}
