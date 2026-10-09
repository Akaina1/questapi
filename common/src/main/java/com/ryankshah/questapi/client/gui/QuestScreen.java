package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.ObjectiveEntry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.RewardChoice;
import com.ryankshah.questapi.api.quest.condition.QuestCondition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.api.quest.objective.impl.DeliverItemObjective;
import com.ryankshah.questapi.api.quest.reward.QuestReward;
import com.ryankshah.questapi.client.ClientQuestDataCache;
import com.ryankshah.questapi.client.network.ClientQuestNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The default quest book GUI. Purely a consumer of {@link ClientQuestDataCache}: it never computes
 * quest state itself, it only displays whatever the server last synced and sends request payloads
 * for player actions.
 * <p>
 * A row of state tabs (Ongoing / Completed / Failed, plus Available when manual start is allowed)
 * sits above a three-column layout: a scrollable category sidebar, a scrollable quest list, and a
 * detail panel.
 * The panel size adapts to the window so it never exceeds the screen, and every column scrolls or
 * word-wraps instead of overflowing its bounds.
 */
public final class QuestScreen extends Screen {

    private static final int CATEGORY_WIDTH = 90;
    private static final int LIST_WIDTH = 130;
    private static final int MARGIN = 6;
    private static final int GAP = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int PROGRESS_BAR_HEIGHT = 3;
    private static final int GROUP_INDENT = 8;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int ACTION_BUTTON_GAP = 2;

    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 2;

    /**
     * Top-level filter over quest states. {@code AVAILABLE} is only offered when the server lets
     * players start quests from the book; otherwise quests that are not yet started stay hidden.
     */
    private enum Tab {
        ONGOING("questapi.gui.tab.ongoing", QuestState.ACTIVE, QuestState.COMPLETED),
        COMPLETED("questapi.gui.tab.completed", QuestState.REWARDED),
        FAILED("questapi.gui.tab.failed", QuestState.FAILED),
        AVAILABLE("questapi.gui.tab.available", QuestState.AVAILABLE, QuestState.LOCKED, QuestState.ABANDONED);

        private final String translationKey;
        private final List<QuestState> states;

        Tab(String translationKey, QuestState... states) {
            this.translationKey = translationKey;
            this.states = List.of(states);
        }

        boolean matches(QuestState state) {
            return states.contains(state);
        }
    }

    private static Tab[] visibleTabs(boolean includeAvailable) {
        return includeAvailable
                ? new Tab[]{Tab.AVAILABLE, Tab.ONGOING, Tab.COMPLETED, Tab.FAILED}
                : new Tab[]{Tab.ONGOING, Tab.COMPLETED, Tab.FAILED};
    }

    private final ClientQuestDataCache cache = ClientQuestDataCache.INSTANCE;
    private Tab selectedTab = Tab.ONGOING;
    private final List<Button> tabButtons = new ArrayList<>();
    private boolean tabsIncludeAvailable;
    private int columnY;
    private int columnHeight;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;
    private int detailX;
    private int detailY;
    private int detailWidth;
    private int lastSeenRevision = -1;

    private Identifier selectedCategory;
    private Quest selectedQuest;
    private CategoryListWidget categoryList;
    private QuestListWidget questList;
    private Button actionButton;
    private Button trackButton;
    private final List<DeliverButtonBounds> deliverButtons = new ArrayList<>();
    private int lastMouseX;
    private int lastMouseY;
    /** The Y range detail text may currently be drawn in; anything outside it is skipped. */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;
    private int detailScroll;
    /** Height of the scrollable detail text as of the last frame, used to limit scrolling. */
    private int detailContentHeight;

    public QuestScreen() {
        super(Component.translatable("questapi.gui.title"));
    }

    public static void open() {
        ClientQuestNetworking.requestSync();
        Minecraft.getInstance().gui.setScreen(new QuestScreen());
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(460, this.width - 16);
        this.panelHeight = Math.min(240, this.height - 16);
        this.leftPos = (this.width - panelWidth) / 2;
        this.topPos = (this.height - panelHeight) / 2;

        this.columnY = topPos + MARGIN + TAB_HEIGHT + MARGIN;
        this.columnHeight = topPos + panelHeight - MARGIN - columnY;

        int categoryX = leftPos + MARGIN;
        categoryList = new CategoryListWidget(minecraft, categoryX, columnY, CATEGORY_WIDTH, columnHeight, this::selectCategory);
        addRenderableWidget(categoryList);

        int listX = categoryX + CATEGORY_WIDTH + GAP;
        questList = new QuestListWidget(minecraft, listX, columnY, LIST_WIDTH, columnHeight, this::selectQuest);
        addRenderableWidget(questList);

        this.detailX = listX + LIST_WIDTH + GAP + 4;
        this.detailY = columnY + 4;
        this.detailWidth = leftPos + panelWidth - MARGIN - detailX;

        layoutTabs();
        refreshLists();
        refreshActionButton();
        lastSeenRevision = cache.revision();
    }

    private void layoutTabs() {
        for (Button button : tabButtons) {
            removeWidget(button);
        }
        tabButtons.clear();

        tabsIncludeAvailable = cache.manualActions().start();
        if (!tabsIncludeAvailable && selectedTab == Tab.AVAILABLE) {
            selectedTab = Tab.ONGOING;
        }
        Tab[] tabs = visibleTabs(tabsIncludeAvailable);
        int totalWidth = panelWidth - 2 * MARGIN;
        int tabWidth = (totalWidth - (tabs.length - 1) * TAB_GAP) / tabs.length;
        int tabY = topPos + MARGIN;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Button button = Button.builder(Component.translatable(tab.translationKey), b -> selectTab(tab))
                    .bounds(leftPos + MARGIN + i * (tabWidth + TAB_GAP), tabY, tabWidth, TAB_HEIGHT).build();
            button.active = tab != selectedTab;
            tabButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void selectTab(Tab tab) {
        this.selectedTab = tab;
        this.selectedQuest = null;
        this.detailScroll = 0;
        layoutTabs();
        refreshLists();
        refreshActionButton();
    }

    private List<Quest> questsInTab(Identifier categoryId) {
        return cache.questsInCategory(categoryId).stream()
                .filter(quest -> selectedTab.matches(cache.getState(quest.id())))
                .toList();
    }

    /**
     * Rebuilds the category sidebar and quest list for the current tab. Categories with nothing to
     * show in this tab are dropped so the player never lands on an empty list.
     */
    private void refreshLists() {
        List<QuestCategory> visible = cache.categories().stream()
                .filter(category -> !questsInTab(category.id()).isEmpty())
                .toList();
        categoryList.setCategories(visible, cache);

        if (selectedCategory == null || visible.stream().noneMatch(category -> category.id().equals(selectedCategory))) {
            selectedCategory = visible.isEmpty() ? null : visible.get(0).id();
            selectedQuest = null;
        }

        List<Quest> quests = selectedCategory == null ? List.of() : questsInTab(selectedCategory);
        questList.setQuests(quests, cache);

        if (selectedQuest != null) {
            Identifier selectedId = selectedQuest.id();
            selectedQuest = quests.stream().filter(quest -> quest.id().equals(selectedId)).findFirst().orElse(null);
        }
    }

    private void selectCategory(QuestCategory category) {
        this.selectedCategory = category.id();
        this.selectedQuest = null;
        this.detailScroll = 0;
        refreshLists();
        refreshActionButton();
    }

    private void selectQuest(Quest quest) {
        if (selectedQuest == null || !selectedQuest.id().equals(quest.id())) {
            this.detailScroll = 0;
        }
        this.selectedQuest = quest;
        refreshActionButton();
    }

    private void refreshActionButton() {
        if (actionButton != null) {
            removeWidget(actionButton);
            actionButton = null;
        }
        if (trackButton != null) {
            removeWidget(trackButton);
            trackButton = null;
        }
        if (selectedQuest == null) {
            return;
        }
        QuestState state = cache.getState(selectedQuest.id());
        ManualQuestActions allowed = cache.manualActions();
        int buttonY = topPos + panelHeight - MARGIN - 20;

        switch (state) {
            case AVAILABLE -> {
                if (allowed.start()) {
                    actionButton = Button.builder(Component.translatable("questapi.gui.action.start"),
                                    b -> ClientQuestNetworking.requestStartQuest(selectedQuest.id()))
                            .bounds(detailX, buttonY, detailWidth, 20).build();
                }
            }
            case ACTIVE -> {
                // Track/Untrack and Abandon share one row; without Abandon, Track takes the full width.
                int trackWidth = detailWidth;
                if (allowed.abandon() && selectedQuest.abandonable()) {
                    trackWidth = (detailWidth - ACTION_BUTTON_GAP) / 2;
                    int abandonX = detailX + trackWidth + ACTION_BUTTON_GAP;
                    actionButton = Button.builder(Component.translatable("questapi.gui.action.abandon"),
                                    b -> confirmAbandon(selectedQuest.id()))
                            .bounds(abandonX, buttonY, detailX + detailWidth - abandonX, 20).build();
                }
                boolean tracked = cache.isTracked(selectedQuest.id());
                Component trackLabel = Component.translatable(tracked ? "questapi.gui.action.untrack" : "questapi.gui.action.track");
                trackButton = Button.builder(trackLabel,
                                b -> ClientQuestNetworking.requestToggleTrackQuest(selectedQuest.id()))
                        .bounds(detailX, buttonY, trackWidth, 20).build();
            }
            default -> {
            }
        }
        if (actionButton != null) {
            addRenderableWidget(actionButton);
        }
        if (trackButton != null) {
            addRenderableWidget(trackButton);
        }
    }

    private void confirmAbandon(Identifier questId) {
        minecraft.gui.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        ClientQuestNetworking.requestAbandonQuest(questId);
                    }
                    minecraft.gui.setScreen(this);
                },
                Component.translatable("questapi.gui.abandon.confirm.title"),
                Component.translatable("questapi.gui.abandon.confirm.message")));
    }

    @Override
    public void tick() {
        if (cache.revision() != lastSeenRevision) {
            lastSeenRevision = cache.revision();
            if (tabsIncludeAvailable != cache.manualActions().start()) {
                layoutTabs();
            }
            refreshLists();
            refreshActionButton();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.extractRenderStateWithTooltipAndSubtitles already called extractBackground() once
        // before invoking this method - calling it again throws "Can only blur once per frame".
        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, 0xE0202020);

        // One consistent flat background behind all three columns, drawn before the list widgets so
        // it shows through as their backdrop instead of the mismatched vanilla list textures.
        int categoryX = leftPos + MARGIN;
        int listX = categoryX + CATEGORY_WIDTH + GAP;
        graphics.fill(categoryX, columnY, categoryX + CATEGORY_WIDTH, columnY + columnHeight, 0x60000000);
        graphics.fill(listX, columnY, listX + LIST_WIDTH, columnY + columnHeight, 0x60000000);
        graphics.fill(detailX - 4, detailY - 4, leftPos + panelWidth - MARGIN, topPos + panelHeight - MARGIN, 0x60000000);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        deliverButtons.clear();
        if (selectedQuest != null) {
            renderQuestDetail(graphics, selectedQuest, detailX, detailY, detailWidth);
        } else if (selectedCategory == null) {
            drawWrapped(graphics, Component.translatable("questapi.gui.empty"), detailX, detailY, detailWidth, 0xFF888888);
        }
    }

    /**
     * Word-wraps {@code text} to {@code width} and draws it starting at {@code y}, returning the new
     * cursor Y position. Used for every piece of detail-panel text that could plausibly be longer
     * than the column is wide (descriptions, objectives, rewards, prerequisites).
     */
    private int drawWrapped(GuiGraphicsExtractor graphics, FormattedText text, int x, int y, int width, int color) {
        int cursorY = y;
        for (var line : font.split(text, width)) {
            if (isVisible(cursorY, LINE_HEIGHT)) {
                graphics.text(font, line, x, cursorY, color);
            }
            cursorY += LINE_HEIGHT;
        }
        return cursorY;
    }

    /**
     * Whether something {@code height} tall drawn at {@code y} lies completely inside the region
     * that is currently being drawn (see {@link #clipTop}). Content is culled line by line instead
     * of being cut off halfway, which keeps scrolled text readable.
     */
    private boolean isVisible(int y, int height) {
        return y >= clipTop && y + height <= clipBottom;
    }

    private int wrappedHeight(FormattedText text, int width) {
        return font.split(text, width).size() * LINE_HEIGHT;
    }

    /**
     * The lowest Y the detail text may reach: just above the action buttons if there are any, so
     * text never runs under them.
     */
    private int detailAreaBottom() {
        int bottom = topPos + panelHeight - MARGIN - 4;
        for (Button button : new Button[]{actionButton, trackButton}) {
            if (button != null) {
                bottom = Math.min(bottom, button.getY() - 4);
            }
        }
        return bottom;
    }

    /**
     * What the book shows for a quest's reward choice: before the claim a read-only list with one
     * line per choice, after it only the choice the player took. The player picks at the turn-in
     * (an NPC or command), never in the book.
     *
     * @param header      the heading line
     * @param headerColor its color
     * @param lines       one line per choice, empty once a choice was taken
     */
    private record ChoiceBlock(Component header, int headerColor, List<Component> lines) {
    }

    private Optional<ChoiceBlock> choiceBlock(Quest quest, QuestState state, QuestProgress progress) {
        if (quest.rewardChoices().isEmpty()) {
            return Optional.empty();
        }
        if (state == QuestState.REWARDED) {
            return progress.chosenReward()
                    .flatMap(quest::rewardChoice)
                    .map(choice -> new ChoiceBlock(
                            Component.translatable("questapi.gui.reward_chosen", choice.label()), 0xFF55FF55, List.of()));
        }
        List<Component> lines = new ArrayList<>();
        for (RewardChoice choice : quest.rewardChoices()) {
            MutableComponent summary = Component.empty();
            for (int i = 0; i < choice.rewards().size(); i++) {
                if (i > 0) {
                    summary.append(Component.literal(", "));
                }
                summary.append(choice.rewards().get(i).describe());
            }
            lines.add(Component.literal("- ").append(choice.label()).append(Component.literal(" (")).append(summary).append(Component.literal(")")));
        }
        return Optional.of(new ChoiceBlock(Component.translatable("questapi.gui.reward_choices"), 0xFFFFAA00, lines));
    }

    /**
     * Height of the always-visible rewards block (rewards, the reward choice, plus failure rewards
     * if the quest has any).
     */
    private int rewardsHeight(Quest quest, QuestState state, QuestProgress progress, int width) {
        int height = wrappedHeight(Component.translatable("questapi.gui.rewards"), width);
        for (QuestReward reward : quest.rewards()) {
            height += wrappedHeight(Component.literal("- ").append(reward.describe()), width);
        }
        Optional<ChoiceBlock> choices = choiceBlock(quest, state, progress);
        if (choices.isPresent()) {
            height += 4 + wrappedHeight(choices.get().header(), width);
            for (Component line : choices.get().lines()) {
                height += wrappedHeight(line, width);
            }
        }
        List<QuestReward> failureRewards = quest.failureRewards();
        if (!failureRewards.isEmpty()) {
            height += 4 + wrappedHeight(Component.translatable("questapi.gui.failure_rewards"), width);
            for (QuestReward reward : failureRewards) {
                height += wrappedHeight(Component.literal("- ").append(reward.describe()), width);
            }
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedQuest != null && mouseX >= detailX - 4 && mouseX < leftPos + panelWidth - MARGIN
                && mouseY >= detailY && mouseY < detailAreaBottom()) {
            detailScroll = Math.max(0, detailScroll - (int) Math.round(scrollY * LINE_HEIGHT * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Draws the selected quest in three parts: a fixed header (icon, title, state), a scrollable
     * middle (limits, description, prerequisites, objectives) and a fixed footer with the rewards,
     * so a long objective list can never push the rewards or run under the action buttons.
     */
    private void renderQuestDetail(GuiGraphicsExtractor graphics, Quest quest, int x, int y, int fullWidth) {
        QuestState state = cache.getState(quest.id());
        QuestProgress progress = cache.getProgress(quest.id());
        int width = fullWidth - SCROLLBAR_WIDTH;
        int areaBottom = detailAreaBottom();

        clipTop = y;
        clipBottom = areaBottom;
        graphics.item(quest.icon(), x, y);
        int titleTextWidth = width - 22;
        int headerY = drawWrapped(graphics, quest.title(), x + 22, y + 1, titleTextWidth, 0xFFFFFF55);
        headerY = Math.max(headerY, y + 12);
        headerY = drawWrapped(graphics, QuestGuiText.stateLabel(quest, state), x + 22, headerY, titleTextWidth, QuestGuiText.stateColor(state));
        headerY += 2;

        // The rewards get the bottom of the area (at most half of what is left under the header);
        // the scrollable middle gets everything between the header and the rewards.
        int footerHeight = Math.min(rewardsHeight(quest, state, progress, width), Math.max(0, (areaBottom - headerY) / 2));
        int footerTop = areaBottom - footerHeight;
        int viewTop = headerY;
        int viewBottom = footerTop - 4;
        int viewHeight = Math.max(0, viewBottom - viewTop);
        detailScroll = Math.max(0, Math.min(detailScroll, detailContentHeight - viewHeight));

        clipTop = viewTop;
        clipBottom = viewBottom;
        int contentStart = viewTop - detailScroll;
        int cursorY = contentStart;

        QuestFailureRules failureRules = quest.failure().orElse(null);
        if (failureRules != null) {
            Component limitLabel = QuestGuiText.timeLimitLabel(quest);
            if (limitLabel != null) {
                cursorY = drawWrapped(graphics, limitLabel, x, cursorY, width, 0xFFAAAAAA);
            }
            OptionalLong remainingTicks = cache.remainingLimitTicks(quest);
            if (remainingTicks.isPresent()) {
                cursorY = drawWrapped(graphics, QuestGuiText.timeRemainingLabel(quest, remainingTicks.getAsLong(), cache.ticksPerGameDay()),
                        x, cursorY, width, 0xFFFFAA00);
            }
            cursorY = drawWrapped(graphics, QuestGuiText.retryLabel(failureRules), x, cursorY, width, 0xFF999999);
            Component failureReason = failureRules.reason().orElse(null);
            if (state == QuestState.FAILED && failureReason != null) {
                cursorY = drawWrapped(graphics, failureReason, x, cursorY, width, 0xFFFF8888);
            }
            cursorY += 2;
        }

        cursorY = drawWrapped(graphics, quest.description(), x, cursorY, width, 0xFFCCCCCC);
        cursorY += 4;

        if (state == QuestState.LOCKED && !quest.prerequisites().isEmpty()) {
            cursorY = drawWrapped(graphics, Component.translatable("questapi.gui.prerequisites"), x, cursorY, width, 0xFFFF5555);
            for (QuestCondition condition : quest.prerequisites()) {
                cursorY = drawWrapped(graphics, Component.literal("- ").append(condition.describe()), x, cursorY, width, 0xFFAA8888);
            }
            cursorY += 4;
        }

        cursorY = drawWrapped(graphics, Component.translatable("questapi.gui.objectives"), x, cursorY, width, 0xFF55FFFF);
        List<ObjectiveEntry> entries = quest.objectiveEntries();
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            ObjectiveEntry entry = entries.get(entryIndex);
            int first = quest.firstObjectiveOf(entryIndex);
            if (!progress.objectiveUnlocked(quest, first)) {
                // Optional entries of a step that has not opened are not drawn at all, so they
                // can't hint at what is coming.
                if (!entry.optional()) {
                    cursorY = drawWrapped(graphics, Component.translatable("questapi.gui.objective.hidden"), x, cursorY, width, 0xFF777777);
                    cursorY += PROGRESS_BAR_HEIGHT + 2;
                }
                continue;
            }
            if (!entry.isGroup()) {
                cursorY = drawObjective(graphics, quest, progress, state, first, x, cursorY, width, entry.optional());
                continue;
            }
            Component header = entry.isAllOf()
                    ? Component.translatable("questapi.gui.objective.complete_all")
                    : entry.required() == 1
                            ? Component.translatable("questapi.gui.objective.choose_one")
                            : Component.translatable("questapi.gui.objective.choose_n", entry.required());
            if (entry.optional()) {
                header = header.copy().append(Component.literal(" ")).append(Component.translatable("questapi.gui.objective.optional"));
            }
            cursorY = drawWrapped(graphics, header, x, cursorY, width, progress.entryComplete(quest, entryIndex) ? 0xFF55FF55 : 0xFFAAAAAA);
            for (int option = 0; option < entry.options().size(); option++) {
                cursorY = drawObjective(graphics, quest, progress, state, first + option, x + GROUP_INDENT, cursorY, width - GROUP_INDENT, false);
            }
        }
        detailContentHeight = cursorY - contentStart;

        if (detailContentHeight > viewHeight && viewHeight > 0) {
            int barX = x + fullWidth - 3;
            int thumbHeight = Math.max(8, viewHeight * viewHeight / detailContentHeight);
            int thumbY = viewTop + (viewHeight - thumbHeight) * detailScroll / (detailContentHeight - viewHeight);
            graphics.fill(barX, viewTop, barX + 2, viewBottom, 0x40FFFFFF);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        clipTop = footerTop;
        clipBottom = areaBottom;
        graphics.fill(x, footerTop - 3, x + fullWidth, footerTop - 2, 0x40FFFFFF);
        cursorY = footerTop;
        cursorY = drawWrapped(graphics, Component.translatable("questapi.gui.rewards"), x, cursorY, width, 0xFFFFAA00);
        for (QuestReward reward : quest.rewards()) {
            cursorY = drawWrapped(graphics, Component.literal("- ").append(reward.describe()), x, cursorY, width, 0xFFDDDDDD);
        }

        Optional<ChoiceBlock> choices = choiceBlock(quest, state, progress);
        if (choices.isPresent()) {
            cursorY += 4;
            cursorY = drawWrapped(graphics, choices.get().header(), x, cursorY, width, choices.get().headerColor());
            for (Component line : choices.get().lines()) {
                cursorY = drawWrapped(graphics, line, x, cursorY, width, 0xFFDDDDDD);
            }
        }

        List<QuestReward> failureRewards = quest.failureRewards();
        if (!failureRewards.isEmpty()) {
            cursorY += 4;
            cursorY = drawWrapped(graphics, Component.translatable("questapi.gui.failure_rewards"), x, cursorY, width, 0xFFFF8888);
            for (QuestReward reward : failureRewards) {
                cursorY = drawWrapped(graphics, Component.literal("- ").append(reward.describe()), x, cursorY, width, 0xFFDDDDDD);
            }
        }
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    /**
     * Draws one objective line with its progress bar (and its deliver button, if it has one) and
     * returns the new cursor Y. An unfinished option of a group that is already satisfied is
     * greyed out, because it no longer counts.
     */
    private int drawObjective(GuiGraphicsExtractor graphics, Quest quest, QuestProgress progress, QuestState state,
                              int index, int x, int y, int width, boolean optionalTag) {
        ObjectiveDefinition objective = quest.objectives().get(index);
        ObjectiveProgress op = progress.objectives().getOrDefault(index, ObjectiveProgress.empty());
        boolean passedOver = progress.objectiveFrozen(quest, index);
        String amountText = " (" + op.current() + "/" + objective.targetAmount() + ")";
        int color = op.complete() ? 0xFF55FF55 : passedOver ? 0xFF666666 : 0xFFDDDDDD;
        boolean deliverable = objective instanceof DeliverItemObjective && state == QuestState.ACTIVE && !op.complete()
                && !passedOver && cache.manualActions().deliver();
        int lineWidth = width;
        int deliverButtonWidth = 0;
        Component deliverLabel = null;
        if (deliverable) {
            deliverLabel = Component.translatable("questapi.gui.action.deliver");
            deliverButtonWidth = font.width(deliverLabel) + 8;
            lineWidth = Math.max(20, width - deliverButtonWidth - 4);
        }
        MutableComponent objectiveLine = optionalTag
                ? Component.translatable("questapi.gui.objective.optional").append(Component.literal(" "))
                : Component.empty();
        objectiveLine.append(objective.describe()).append(Component.literal(amountText));
        int cursorY = y;
        int lineStartY = cursorY;
        cursorY = drawWrapped(graphics, objectiveLine, x, cursorY, lineWidth, color);

        int target = objective.targetAmount();
        float ratio = target > 0 ? Math.min(1f, (float) op.current() / target) : 0f;
        int filledWidth = Math.round(width * ratio);
        int barColor = op.complete() ? 0xFF55FF55 : passedOver ? 0xFF555555 : 0xFF55FFFF;
        if (isVisible(cursorY, PROGRESS_BAR_HEIGHT)) {
            graphics.fill(x, cursorY, x + width, cursorY + PROGRESS_BAR_HEIGHT, 0x60000000);
            if (filledWidth > 0) {
                graphics.fill(x, cursorY, x + filledWidth, cursorY + PROGRESS_BAR_HEIGHT, barColor);
            }
        }
        cursorY += PROGRESS_BAR_HEIGHT + 2;

        if (deliverable && isVisible(lineStartY, LINE_HEIGHT)) {
            int bx = x + width - deliverButtonWidth;
            int by = lineStartY;
            boolean hovered = lastMouseX >= bx && lastMouseX < bx + deliverButtonWidth && lastMouseY >= by && lastMouseY < by + LINE_HEIGHT;
            graphics.fill(bx, by, x + width, by + LINE_HEIGHT, hovered ? 0xA000CC00 : 0x8000AA00);
            graphics.text(font, deliverLabel, bx + 4, by + 1, 0xFFFFFFFF);
            deliverButtons.add(new DeliverButtonBounds(index, bx, by, deliverButtonWidth, LINE_HEIGHT));
            if (hovered) {
                int remaining = objective.targetAmount() - op.current();
                graphics.setTooltipForNextFrame(font, Component.translatable("questapi.gui.action.deliver.tooltip", remaining), lastMouseX, lastMouseY);
            }
        }
        return cursorY;
    }

    private record DeliverButtonBounds(int objectiveIndex, int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (selectedQuest != null && cache.getState(selectedQuest.id()) == QuestState.ACTIVE) {
            QuestProgress progress = cache.getProgress(selectedQuest.id());
            List<ObjectiveDefinition> objectives = selectedQuest.objectives();
            for (DeliverButtonBounds bounds : deliverButtons) {
                if (!bounds.contains(event.x(), event.y())) {
                    continue;
                }
                ObjectiveDefinition objective = objectives.get(bounds.objectiveIndex());
                ObjectiveProgress op = progress.objectives().getOrDefault(bounds.objectiveIndex(), ObjectiveProgress.empty());
                int remaining = objective.targetAmount() - op.current();
                if (remaining > 0) {
                    ClientQuestNetworking.requestDeliverItems(selectedQuest.id(), bounds.objectiveIndex(), remaining);
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
