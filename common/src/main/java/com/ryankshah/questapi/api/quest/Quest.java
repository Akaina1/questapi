package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Immutable, static definition of a quest: its identity, presentation, objectives, rewards and
 * unlock conditions. Holds no player-specific state whatsoever - see {@link QuestProgress} for that.
 * <p>
 * Build one with {@link #builder(Identifier)}.
 */
public final class Quest {

    private final Identifier id;
    private final Identifier categoryId;
    private final QuestDisplay display;
    private final QuestLifecycle lifecycle;
    private final List<ObjectiveEntry> entries;
    private final List<ObjectiveDefinition> objectives;
    private final int[] entryOfObjective;
    private final int[] firstObjectiveOfEntry;
    private final int[] stepOfEntry;
    private final List<QuestReward> rewards;
    private final List<RewardChoice> rewardChoices;
    private final List<QuestCondition> prerequisites;
    private final Optional<QuestFailureRules> failure;
    private final Optional<QuestToastOverrides> toastOverrides;
    private final Optional<Identifier> questline;
    private final Optional<Integer> chapter;
    private final boolean chapterFinal;

    private Quest(Builder builder) {
        this.id = builder.id;
        this.categoryId = builder.categoryId;
        this.questline = Optional.ofNullable(builder.questline);
        this.chapter = Optional.ofNullable(builder.chapter);
        this.chapterFinal = builder.chapterFinal;
        this.display = new QuestDisplay(builder.title, builder.description, builder.icon, builder.sortOrder);
        this.lifecycle = new QuestLifecycle(builder.autoActivate, builder.sequential,
                builder.repeatable ? Optional.of(new QuestRepeat(builder.resetMode, builder.resetAmount)) : Optional.empty());
        this.entries = List.copyOf(builder.entries);
        List<ObjectiveDefinition> flat = new java.util.ArrayList<>();
        this.firstObjectiveOfEntry = new int[entries.size()];
        this.stepOfEntry = new int[entries.size()];
        int step = 0;
        boolean seenRequired = false;
        List<Integer> owners = new java.util.ArrayList<>();
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            ObjectiveEntry entry = entries.get(entryIndex);
            firstObjectiveOfEntry[entryIndex] = flat.size();
            for (ObjectiveDefinition option : entry.options()) {
                flat.add(option);
                owners.add(entryIndex);
            }
            // An optional entry shares the step of the required entry written before it, or the
            // first step when it is written before any required entry.
            if (!entry.optional()) {
                if (seenRequired) {
                    step++;
                }
                seenRequired = true;
            }
            stepOfEntry[entryIndex] = step;
        }
        this.objectives = List.copyOf(flat);
        this.entryOfObjective = owners.stream().mapToInt(Integer::intValue).toArray();
        this.rewards = List.copyOf(builder.rewards);
        this.rewardChoices = List.copyOf(builder.rewardChoices);
        this.prerequisites = List.copyOf(builder.prerequisites);
        this.failure = Optional.ofNullable(builder.failure);
        this.toastOverrides = Optional.ofNullable(builder.toastOverrides);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    /**
     * The presentation group (title, description, icon, sort order).
     */
    public QuestDisplay display() {
        return display;
    }

    /**
     * The behaviour group (auto-activation, sequential objectives, repeat cooldown).
     */
    public QuestLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * How this quest can fail, or empty if it only fails through {@code QuestManager#failQuest}.
     */
    public Optional<QuestFailureRules> failure() {
        return failure;
    }

    /**
     * Per-quest replacements for the default toast titles, if any.
     */
    public Optional<QuestToastOverrides> toastOverrides() {
        return toastOverrides;
    }

    public Component title() {
        return display.title();
    }

    public Component description() {
        return display.description();
    }

    public ItemStack icon() {
        return display.icon();
    }

    public Identifier categoryId() {
        return categoryId;
    }

    /**
     * The questline this quest belongs to, if any. A quest in a closed questline can never be
     * started; see {@code QuestlineDefinition}.
     */
    public Optional<Identifier> questline() {
        return questline;
    }

    /**
     * The chapter this quest itself is placed in, if any. A quest without one still belongs to the
     * chapter of its questline; use {@code QuestRegistry#chapterOf} for the effective chapter.
     */
    public Optional<Integer> chapter() {
        return chapter;
    }

    /**
     * Whether handing this quest in ends its chapter: everything left in the chapter fails or is
     * locked for good, and the next chapter opens.
     */
    public boolean chapterFinal() {
        return chapterFinal;
    }

    /**
     * Every objective in a flat list, the options of a group one after another. Saved progress and
     * objective indexes everywhere (delivery, {@code fail_on} steps) refer to positions in this list.
     */
    public List<ObjectiveDefinition> objectives() {
        return objectives;
    }

    /**
     * The objective list as written: single objectives and groups, each possibly optional.
     */
    public List<ObjectiveEntry> objectiveEntries() {
        return entries;
    }

    /**
     * The entry that the objective at flat position {@code objectiveIndex} belongs to.
     */
    public int entryOf(int objectiveIndex) {
        return entryOfObjective[objectiveIndex];
    }

    /**
     * The flat position of the first objective of entry {@code entryIndex}.
     */
    public int firstObjectiveOf(int entryIndex) {
        return firstObjectiveOfEntry[entryIndex];
    }

    /**
     * The step of an entry: one step per required entry, counting from 0. Optional entries share
     * the step of the required entry written before them (or the first step if none comes first).
     * In a sequential quest a step opens when the required entries of all earlier steps are done.
     */
    public int stepOf(int entryIndex) {
        return stepOfEntry[entryIndex];
    }

    /**
     * The step of the objective at flat position {@code objectiveIndex}.
     */
    public int stepOfObjective(int objectiveIndex) {
        return stepOfEntry[entryOfObjective[objectiveIndex]];
    }

    /**
     * The choices the player picks one of when claiming, on top of {@link #rewards()}. Empty for a
     * quest without a reward choice; such a quest is claimed with no choice at all.
     */
    public List<RewardChoice> rewardChoices() {
        return rewardChoices;
    }

    /**
     * The choice with the given id, if this quest has it.
     */
    public Optional<RewardChoice> rewardChoice(String choiceId) {
        return rewardChoices.stream().filter(choice -> choice.id().equals(choiceId)).findFirst();
    }

    public List<QuestReward> rewards() {
        return rewards;
    }

    /**
     * Rewards claimable once after this quest failed, or an empty list if it has none.
     */
    public List<QuestReward> failureRewards() {
        return failure.map(QuestFailureRules::rewards).orElse(List.of());
    }

    public List<QuestCondition> prerequisites() {
        return prerequisites;
    }

    /**
     * If {@code true}, this quest skips the {@code AVAILABLE} state and becomes {@code ACTIVE} the
     * moment its prerequisites are satisfied, without requiring the player to manually start it.
     */
    public boolean autoActivate() {
        return lifecycle.autoActivate();
    }

    /**
     * Lower values are displayed first within a category.
     */
    public int sortOrder() {
        return display.sortOrder();
    }

    /**
     * Whether a failed instance of this quest is reset straight back to its starting state. Quests
     * without failure rules are not retryable.
     */
    public boolean retryable() {
        return failure.map(QuestFailureRules::retryable).orElse(false);
    }

    /**
     * If {@code true}, this quest automatically returns to {@code AVAILABLE} some time after being
     * rewarded, instead of staying in the terminal {@code REWARDED} state forever.
     */
    public boolean repeatable() {
        return lifecycle.repeat().isPresent();
    }

    /**
     * How this quest's cooldown is measured, or {@code null} to use the server's configured default
     * (see {@code questapi.properties}). Meaningless unless {@link #repeatable()} is {@code true}.
     */
    public ResetMode resetMode() {
        return lifecycle.repeat().map(QuestRepeat::resetMode).orElse(null);
    }

    /**
     * The cooldown length: hours if the resolved {@link ResetMode} is {@link ResetMode#WALL_CLOCK},
     * in-game days if it is {@link ResetMode#IN_GAME_DAY}. Meaningless unless {@link #repeatable()}
     * is {@code true}.
     */
    public int resetAmount() {
        return lifecycle.repeat().map(QuestRepeat::amount).orElse(0);
    }

    /**
     * If {@code true}, objectives must be completed in order: an objective ignores events (and
     * cannot be delivered to) until every objective before it is complete, and the default GUI
     * hides the later steps until they unlock.
     *
     * @see QuestProgress#objectiveUnlocked(Quest, int)
     */
    public boolean sequential() {
        return lifecycle.sequential();
    }

    public static final class Builder {
        private final Identifier id;
        private Component title = Component.literal("Untitled Quest");
        private Component description = Component.empty();
        private ItemStack icon = ItemStack.EMPTY;
        private Identifier categoryId;
        private final List<ObjectiveEntry> entries = new java.util.ArrayList<>();
        private final List<QuestReward> rewards = new java.util.ArrayList<>();
        private final List<RewardChoice> rewardChoices = new java.util.ArrayList<>();
        private final List<QuestCondition> prerequisites = new java.util.ArrayList<>();
        private boolean autoActivate = false;
        private int sortOrder = 0;
        private boolean repeatable = false;
        private ResetMode resetMode = null;
        private int resetAmount = 0;
        private boolean sequential = false;
        private QuestFailureRules failure = null;
        private QuestToastOverrides toastOverrides = null;
        private Identifier questline = null;
        private Integer chapter = null;
        private boolean chapterFinal = false;

        private Builder(Identifier id) {
            this.id = id;
        }

        /**
         * Places this quest in a numbered chapter (1 or higher).
         */
        public Builder chapter(int chapter) {
            if (chapter < 1) {
                throw new IllegalArgumentException("Quest " + id + " chapter must be 1 or higher");
            }
            this.chapter = chapter;
            return this;
        }

        /**
         * Marks this quest as an ending of its chapter.
         */
        public Builder chapterFinal(boolean chapterFinal) {
            this.chapterFinal = chapterFinal;
            return this;
        }

        /**
         * Places this quest in a questline, which can lock it permanently when the questline closes.
         */
        public Builder questline(Identifier questline) {
            this.questline = questline;
            return this;
        }

        public Builder title(Component title) {
            this.title = title;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Builder category(Identifier categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder objective(ObjectiveDefinition objective) {
            this.entries.add(ObjectiveEntry.single(objective, false));
            return this;
        }

        public Builder objectives(List<ObjectiveDefinition> objectives) {
            for (ObjectiveDefinition objective : objectives) {
                objective(objective);
            }
            return this;
        }

        /**
         * Adds an objective that the quest can complete without. In a sequential quest it opens
         * together with the required objective written before it.
         */
        public Builder optionalObjective(ObjectiveDefinition objective) {
            this.entries.add(ObjectiveEntry.single(objective, true));
            return this;
        }

        /**
         * Adds a required choice: the objective counts as done once {@code count} of the
         * {@code options} are complete. Options left unfinished at that point stop progressing.
         */
        public Builder objectiveGroup(int count, List<ObjectiveDefinition> options) {
            this.entries.add(new ObjectiveEntry(options, count, false));
            return this;
        }

        /**
         * Adds an optional choice, see {@link #objectiveGroup(int, List)} and {@link #optionalObjective}.
         */
        public Builder optionalObjectiveGroup(int count, List<ObjectiveDefinition> options) {
            this.entries.add(new ObjectiveEntry(options, count, true));
            return this;
        }

        /**
         * Adds an entry that is already assembled, such as one read from a data file.
         */
        public Builder objectiveEntry(ObjectiveEntry entry) {
            this.entries.add(entry);
            return this;
        }

        public Builder objectiveEntries(List<ObjectiveEntry> entries) {
            this.entries.addAll(entries);
            return this;
        }

        public Builder reward(QuestReward reward) {
            this.rewards.add(reward);
            return this;
        }

        public Builder rewards(List<QuestReward> rewards) {
            this.rewards.addAll(rewards);
            return this;
        }

        /**
         * Adds one option of the quest's reward choice. The player picks exactly one choice when
         * claiming, and gets its rewards on top of the fixed {@link #reward(QuestReward) rewards}.
         * A quest needs at least two choices or none.
         */
        public Builder rewardChoice(RewardChoice choice) {
            this.rewardChoices.add(choice);
            return this;
        }

        public Builder rewardChoices(List<RewardChoice> choices) {
            this.rewardChoices.addAll(choices);
            return this;
        }

        public Builder requires(QuestCondition condition) {
            this.prerequisites.add(condition);
            return this;
        }

        public Builder requires(List<QuestCondition> conditions) {
            this.prerequisites.addAll(conditions);
            return this;
        }

        public Builder autoActivate(boolean autoActivate) {
            this.autoActivate = autoActivate;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        /**
         * Requires objectives to be completed in the order they were added.
         */
        public Builder sequential(boolean sequential) {
            this.sequential = sequential;
            return this;
        }

        /**
         * Sets how this quest can fail (retry behaviour, time limit, fail triggers, reason).
         */
        public Builder failure(QuestFailureRules failure) {
            this.failure = failure;
            return this;
        }

        /**
         * Replaces the title line of this quest's default toasts.
         */
        public Builder toastOverrides(QuestToastOverrides toastOverrides) {
            this.toastOverrides = toastOverrides;
            return this;
        }

        /**
         * Marks this quest repeatable, resetting {@code amount} hours or in-game days (depending on
         * the server's configured default {@link ResetMode}) after it was last claimed.
         */
        public Builder repeatable(int amount) {
            return repeatable(null, amount);
        }

        /**
         * Marks this quest repeatable with an explicit {@link ResetMode}, overriding the server's
         * configured default for this quest only.
         */
        public Builder repeatable(ResetMode mode, int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("Quest " + id + " repeatable amount must be positive");
            }
            this.repeatable = true;
            this.resetMode = mode;
            this.resetAmount = amount;
            return this;
        }

        public Quest build() {
            if (categoryId == null) {
                throw new IllegalStateException("Quest " + id + " has no category assigned");
            }
            if (entries.isEmpty()) {
                throw new IllegalStateException("Quest " + id + " has no objectives");
            }
            if (entries.stream().allMatch(ObjectiveEntry::optional)) {
                throw new IllegalStateException("Quest " + id + " needs at least one objective that is not optional");
            }
            if (rewardChoices.size() == 1) {
                throw new IllegalStateException("Quest " + id + " has a single reward choice; use a normal reward or give it at least 2 choices");
            }
            java.util.Set<String> choiceIds = new java.util.HashSet<>();
            for (RewardChoice choice : rewardChoices) {
                if (!choiceIds.add(choice.id())) {
                    throw new IllegalStateException("Quest " + id + " has two reward choices with the id '" + choice.id() + "'");
                }
            }
            return new Quest(this);
        }
    }
}
