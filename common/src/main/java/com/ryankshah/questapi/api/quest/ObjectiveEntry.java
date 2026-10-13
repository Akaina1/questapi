package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;

import java.util.List;
import java.util.Optional;

/**
 * One line of a quest's objective list as the quest author wrote it: either a single objective or a
 * group of options of which {@code required} must be completed ("choose one", "any two of").
 * <p>
 * Progress is still saved per objective, by the position in the flat {@link Quest#objectives()}
 * list, where the options of a group are numbered one after another. An entry only adds the rules
 * on top of that list: whether it is optional, how many options a group needs, and the optional
 * place it is about.
 *
 * @param options  the objectives of this entry; one for a single objective, two or more for a group
 * @param required how many options must be complete (always 1 for a single objective)
 * @param optional {@code true} if the quest can complete without this entry being done
 * @param location the authored place this step is about, if the quest names one (a hint for compasses
 *                 and maps, see {@link ObjectiveLocation}); it covers every option of a group
 */
public record ObjectiveEntry(List<ObjectiveDefinition> options, int required, boolean optional,
                             Optional<ObjectiveLocation> location) {

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

    /**
     * An entry without a location.
     */
    public ObjectiveEntry(List<ObjectiveDefinition> options, int required, boolean optional) {
        this(options, required, optional, Optional.empty());
    }

    public static ObjectiveEntry single(ObjectiveDefinition objective, boolean optional) {
        return new ObjectiveEntry(List.of(objective), 1, optional, Optional.empty());
    }

    public static ObjectiveEntry single(ObjectiveDefinition objective, boolean optional, Optional<ObjectiveLocation> location) {
        return new ObjectiveEntry(List.of(objective), 1, optional, location);
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
