package com.ryankshah.questapi.api.quest;

import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Immutable, static definition of a quest: its identity, presentation, objectives, rewards and
 * unlock conditions. Holds no player-specific state whatsoever - see {@link QuestProgress} for that.
 * <p>
 * Build one with {@link #builder(Identifier)}.
 */
public final class Quest {

    private final Identifier id;
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final Identifier categoryId;
    private final List<ObjectiveDefinition> objectives;
    private final List<QuestReward> rewards;
    private final List<QuestCondition> prerequisites;
    private final boolean autoActivate;
    private final int sortOrder;
    private final boolean repeatable;
    private final ResetMode resetMode;
    private final int resetAmount;
    private final boolean sequential;

    private Quest(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.icon = builder.icon;
        this.categoryId = builder.categoryId;
        this.objectives = List.copyOf(builder.objectives);
        this.rewards = List.copyOf(builder.rewards);
        this.prerequisites = List.copyOf(builder.prerequisites);
        this.autoActivate = builder.autoActivate;
        this.sortOrder = builder.sortOrder;
        this.repeatable = builder.repeatable;
        this.resetMode = builder.resetMode;
        this.resetAmount = builder.resetAmount;
        this.sequential = builder.sequential;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    public Component title() {
        return title;
    }

    public Component description() {
        return description;
    }

    public ItemStack icon() {
        return icon;
    }

    public Identifier categoryId() {
        return categoryId;
    }

    public List<ObjectiveDefinition> objectives() {
        return objectives;
    }

    public List<QuestReward> rewards() {
        return rewards;
    }

    public List<QuestCondition> prerequisites() {
        return prerequisites;
    }

    /**
     * If {@code true}, this quest skips the {@code AVAILABLE} state and becomes {@code ACTIVE} the
     * moment its prerequisites are satisfied, without requiring the player to manually start it.
     */
    public boolean autoActivate() {
        return autoActivate;
    }

    /**
     * Lower values are displayed first within a category.
     */
    public int sortOrder() {
        return sortOrder;
    }

    /**
     * If {@code true}, this quest automatically returns to {@code AVAILABLE} some time after being
     * rewarded, instead of staying in the terminal {@code REWARDED} state forever.
     */
    public boolean repeatable() {
        return repeatable;
    }

    /**
     * How this quest's cooldown is measured, or {@code null} to use the server's configured default
     * (see {@code questapi.properties}). Meaningless unless {@link #repeatable()} is {@code true}.
     */
    public ResetMode resetMode() {
        return resetMode;
    }

    /**
     * The cooldown length: hours if the resolved {@link ResetMode} is {@link ResetMode#WALL_CLOCK},
     * in-game days if it is {@link ResetMode#IN_GAME_DAY}. Meaningless unless {@link #repeatable()}
     * is {@code true}.
     */
    public int resetAmount() {
        return resetAmount;
    }

    /**
     * If {@code true}, objectives must be completed in order: an objective ignores events (and
     * cannot be delivered to) until every objective before it is complete, and the default GUI
     * hides the later steps until they unlock.
     *
     * @see QuestProgress#objectiveUnlocked(Quest, int)
     */
    public boolean sequential() {
        return sequential;
    }

    public static final class Builder {
        private final Identifier id;
        private Component title = Component.literal("Untitled Quest");
        private Component description = Component.empty();
        private ItemStack icon = ItemStack.EMPTY;
        private Identifier categoryId;
        private final List<ObjectiveDefinition> objectives = new java.util.ArrayList<>();
        private final List<QuestReward> rewards = new java.util.ArrayList<>();
        private final List<QuestCondition> prerequisites = new java.util.ArrayList<>();
        private boolean autoActivate = false;
        private int sortOrder = 0;
        private boolean repeatable = false;
        private ResetMode resetMode = null;
        private int resetAmount = 0;
        private boolean sequential = false;

        private Builder(Identifier id) {
            this.id = id;
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
            this.objectives.add(objective);
            return this;
        }

        public Builder objectives(List<ObjectiveDefinition> objectives) {
            this.objectives.addAll(objectives);
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
            if (objectives.isEmpty()) {
                throw new IllegalStateException("Quest " + id + " has no objectives");
            }
            return new Quest(this);
        }
    }
}
