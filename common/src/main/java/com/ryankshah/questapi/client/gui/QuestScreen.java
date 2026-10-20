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
import com.ryankshah.questapi.client.ClientQuestHooks;
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
import net.minecraft.util.FormattedCharSequence;

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
    private static final int BUTTON_HEIGHT = 20;
    private static final int REWARDS_TOOLTIP_WIDTH = 200;
    private static final int ABANDON_BORDER = 0xFF601010;
    private static final int ABANDON_COLOR = 0xFFC83232;
    private static final int ABANDON_HOVER_COLOR = 0xFFE65050;

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
    private Button pathButton;
    /** Whether {@link #actionButton} is the Abandon button, which is drawn red with black text. */
    private boolean actionIsAbandon;
    /** Set when a button press changes what the buttons should say; they are rebuilt on the next tick. */
    private boolean buttonsDirty;
    private final List<DeliverButtonBounds> deliverButtons = new ArrayList<>();
    private int lastMouseX;
    private int lastMouseY;
    /** The Y range detail text may currently be drawn in; anything outside it is skipped. */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;
    private int detailScroll;
    /** Height of the scrollable detail text as of the last frame, used to limit scrolling. */
    private int detailContentHeight;

    /** Set while another screen draws this one inside itself, see {@link #embed}. */
    private boolean embedded;
    private int embedX;
    private int embedY;
    private int embedWidth;
    private int embedHeight;
    /** The screen to go back to after the abandon confirmation; this screen unless it is embedded. */
    private Screen returnScreen;

    public QuestScreen() {
        super(Component.translatable("questapi.gui.title"));
    }

    /**
     * Opens the quest book. When a mod registered an opener through
     * {@link ClientQuestHooks#setBookOpener}, that opener decides what opens (for example a menu that shows
     * this panel as one of its tabs); otherwise the standalone screen opens.
     */
    public static void open() {
        Optional<Runnable> opener = ClientQuestHooks.bookOpener();
        if (opener.isPresent()) {
            opener.get().run();
            return;
        }
        openStandalone();
    }

    /**
     * Opens the quest book as its own screen, whatever opener is registered.
     */
    public static void openStandalone() {
        ClientQuestNetworking.requestSync();
        Minecraft.getInstance().gui.setScreen(new QuestScreen());
    }

    /**
     * Makes this panel draw and work inside an area of another screen instead of being a screen of its own.
     * The host forwards what it receives: {@link #tick()}, {@link #extractRenderState}, the mouse methods
     * ({@link #mouseClicked}, {@link #mouseReleased}, {@link #mouseDragged}, {@link #mouseScrolled}), and calls
     * this again when its own size changes. The panel does not ask for a sync; the host does that through
     * {@link ClientQuestNetworking#requestSync()}.
     *
     * @param host      the screen that shows the panel; it is the screen shown again after a confirmation
     * @param x         left edge of the area
     * @param y         top edge of the area
     * @param width     width of the area
     * @param height    height of the area
     */
    public void embed(Screen host, int x, int y, int width, int height) {
        this.width = host.width;
        this.height = host.height;
        this.embedded = true;
        this.embedX = x;
        this.embedY = y;
        this.embedWidth = width;
        this.embedHeight = height;
        this.returnScreen = host;
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        if (embedded) {
            this.panelWidth = embedWidth;
            this.panelHeight = embedHeight;
            this.leftPos = embedX;
            this.topPos = embedY;
        } else {
            this.panelWidth = Math.min(460, this.width - 16);
            this.panelHeight = Math.min(240, this.height - 16);
            this.leftPos = (this.width - panelWidth) / 2;
            this.topPos = (this.height - panelHeight) / 2;
        }

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
                .filter(quest -> selectedTab != Tab.COMPLETED || !quest.hideWhenRewarded())
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
        if (pathButton != null) {
            removeWidget(pathButton);
            pathButton = null;
        }
        actionIsAbandon = false;
        if (selectedQuest == null) {
            return;
        }
        QuestState state = cache.getState(selectedQuest.id());
        ManualQuestActions allowed = cache.manualActions();
        Identifier questId = selectedQuest.id();
        int panelBottom = topPos + panelHeight - MARGIN;

        // Second row, full width: Start for an available quest, Abandon for an active one that may be abandoned.
        boolean showStart = state == QuestState.AVAILABLE && allowed.start();
        boolean showAbandon = state == QuestState.ACTIVE && allowed.abandon() && selectedQuest.abandonable();
        actionIsAbandon = showAbandon;
        int secondRowY = panelBottom - BUTTON_HEIGHT;
        int firstRowY = (showStart || showAbandon) ? secondRowY - ACTION_BUTTON_GAP - BUTTON_HEIGHT : secondRowY;
        if (showStart) {
            actionButton = Button.builder(Component.translatable("questapi.gui.action.start"),
                            b -> ClientQuestNetworking.requestStartQuest(questId))
                    .bounds(detailX, secondRowY, detailWidth, BUTTON_HEIGHT).build();
        } else if (showAbandon) {
            actionButton = Button.builder(Component.translatable("questapi.gui.action.abandon"),
                            b -> confirmAbandon(questId))
                    .bounds(detailX, secondRowY, detailWidth, BUTTON_HEIGHT).build();
        }

        // First row, always there: Track/Untrack and Show/Hide Path side by side. Both only work for a
        // quest that is in progress or finished and not handed in, so they are greyed out otherwise.
        boolean trackable = state == QuestState.ACTIVE || state == QuestState.COMPLETED;
        int trackWidth = (detailWidth - ACTION_BUTTON_GAP) / 2;
        int pathX = detailX + trackWidth + ACTION_BUTTON_GAP;
        Component trackLabel = Component.translatable(
                cache.isTracked(questId) ? "questapi.gui.action.untrack" : "questapi.gui.action.track");
        trackButton = Button.builder(trackLabel, b -> ClientQuestNetworking.requestToggleTrackQuest(questId))
                .bounds(detailX, firstRowY, trackWidth, BUTTON_HEIGHT).build();
        trackButton.active = trackable;

        Optional<ClientQuestHooks.PathControl> pathControl = ClientQuestHooks.pathControl();
        boolean pathShown = pathControl.map(control -> control.isPathShown(questId)).orElse(true);
        Component pathLabel = Component.translatable(pathShown ? "questapi.gui.action.hide_path" : "questapi.gui.action.show_path");
        pathButton = Button.builder(pathLabel, b -> {
                    pathControl.ifPresent(control -> control.setPathShown(questId, !pathShown));
                    // The buttons are rebuilt on the next tick, not from inside this click.
                    buttonsDirty = true;
                })
                .bounds(pathX, firstRowY, detailX + detailWidth - pathX, BUTTON_HEIGHT).build();
        pathButton.active = trackable && pathControl.isPresent();

        if (actionButton != null) {
            addRenderableWidget(actionButton);
        }
        addRenderableWidget(trackButton);
        addRenderableWidget(pathButton);
    }

    private void confirmAbandon(Identifier questId) {
        minecraft.gui.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        ClientQuestNetworking.requestAbandonQuest(questId);
                    }
                    minecraft.gui.setScreen(returnScreen != null ? returnScreen : this);
                },
                Component.translatable("questapi.gui.abandon.confirm.title"),
                Component.translatable("questapi.gui.abandon.confirm.message")));
    }

    @Override
    public void tick() {
        if (buttonsDirty) {
            buttonsDirty = false;
            refreshActionButton();
        }
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

        if (actionButton != null && actionIsAbandon) {
            drawAbandonButton(graphics, actionButton, mouseX, mouseY);
        }

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
     * Paints the Abandon button red with black text over the vanilla button, which still receives the
     * clicks. Vanilla buttons have no colour of their own.
     */
    private void drawAbandonButton(GuiGraphicsExtractor graphics, Button button, int mouseX, int mouseY) {
        int left = button.getX();
        int top = button.getY();
        int right = left + button.getWidth();
        int bottom = top + button.getHeight();
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        graphics.fill(left, top, right, bottom, ABANDON_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, hovered ? ABANDON_HOVER_COLOR : ABANDON_COLOR);
        Component label = Component.translatable("questapi.gui.action.abandon");
        graphics.text(font, label, left + (button.getWidth() - font.width(label)) / 2,
                top + (button.getHeight() - 8) / 2, 0xFF000000, false);
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

    /**
     * The top edge of the topmost action button, or the bottom of the panel if there are none.
     */
    private int buttonsTop() {
        int top = topPos + panelHeight - MARGIN;
        for (Button button : new Button[]{actionButton, trackButton, pathButton}) {
            if (button != null) {
                top = Math.min(top, button.getY());
            }
        }
        return top;
    }

    /** The Y of the fixed "Rewards" row, which sits just above the buttons. */
    private int rewardsRowY() {
        return buttonsTop() - 4 - LINE_HEIGHT;
    }

    /**
     * The lowest Y the detail text may reach: just above the rewards row if the quest has one, else
     * just above the action buttons, so text never runs under them.
     */
    private int detailAreaBottom() {
        int bottom = buttonsTop() - 4;
        if (selectedQuest != null && hasRewardsInfo(selectedQuest, cache.getState(selectedQuest.id()),
                cache.getProgress(selectedQuest.id()))) {
            bottom = rewardsRowY() - 4;
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
        int titleX = x + 22;
        int titleWidth = titleTextWidth;
        Component levelTag = QuestGuiText.levelTag(quest);
        if (levelTag != null) {
            graphics.text(font, levelTag, titleX, y + 1, 0xFFFFFFFF);
            int tagWidth = font.width(levelTag) + 3;
            titleX += tagWidth;
            titleWidth -= tagWidth;
        }
        int headerY = drawWrapped(graphics, quest.title(), titleX, y + 1, titleWidth, 0xFFFFFF55);
        // The header is the icon, the level and the title; the state is not repeated here because the
        // quest list already shows it.
        headerY = Math.max(headerY, y + 16);
        headerY += 2;

        // The scrollable middle gets everything between the header and the rewards row (or the buttons).
        int viewTop = headerY;
        int viewBottom = areaBottom;
        int viewHeight = Math.max(0, viewBottom - viewTop);
        detailScroll = Math.max(0, Math.min(detailScroll, detailContentHeight - viewHeight));

        clipTop = viewTop;
        clipBottom = viewBottom;
        int contentStart = viewTop - detailScroll;
        int cursorY = contentStart;

        QuestFailureRules failureRules = quest.failure().orElse(null);
        if (failureRules != null) {
            Component limitLabel = QuestGuiText.timeLimitLabel(quest);
            OptionalLong remainingTicks = cache.remainingLimitTicks(quest);
            Component remainingLabel = remainingTicks.isPresent()
                    ? QuestGuiText.timeRemainingLabel(quest, remainingTicks.getAsLong(), cache.ticksPerGameDay())
                    : null;
            if (limitLabel != null && remainingLabel != null
                    && font.width(limitLabel) + font.width(remainingLabel) + GAP <= width) {
                // The limit and the time left share one row: the limit on the left, the time left on the right.
                if (isVisible(cursorY, LINE_HEIGHT)) {
                    graphics.text(font, limitLabel, x, cursorY, 0xFFAAAAAA);
                    graphics.text(font, remainingLabel, x + width - font.width(remainingLabel), cursorY, 0xFFFFAA00);
                }
                cursorY += LINE_HEIGHT;
            } else {
                if (limitLabel != null) {
                    cursorY = drawWrapped(graphics, limitLabel, x, cursorY, width, 0xFFAAAAAA);
                }
                if (remainingLabel != null) {
                    cursorY = drawWrapped(graphics, remainingLabel, x, cursorY, width, 0xFFFFAA00);
                }
            }
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

        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;

        if (hasRewardsInfo(quest, state, progress)) {
            drawRewardsRow(graphics, quest, state, progress, x, rewardsRowY(), fullWidth);
        }
    }

    /**
     * Whether the quest has anything to show under "Rewards": rewards, a reward choice or failure
     * rewards.
     */
    private boolean hasRewardsInfo(Quest quest, QuestState state, QuestProgress progress) {
        return !quest.rewards().isEmpty() || !quest.failureRewards().isEmpty()
                || choiceBlock(quest, state, progress).isPresent();
    }

    /**
     * The fixed "Rewards" row above the buttons. The rewards themselves are not listed in the panel:
     * hovering the word opens a tooltip with them, which leaves the room to the description and the
     * objectives.
     */
    private void drawRewardsRow(GuiGraphicsExtractor graphics, Quest quest, QuestState state, QuestProgress progress,
                                int x, int rowY, int fullWidth) {
        Component label = Component.translatable("questapi.gui.rewards").withStyle(style -> style.withUnderlined(true));
        int labelWidth = font.width(label);
        boolean hovered = lastMouseX >= x && lastMouseX < x + labelWidth
                && lastMouseY >= rowY && lastMouseY < rowY + LINE_HEIGHT;
        graphics.fill(x, rowY - 3, x + fullWidth, rowY - 2, 0x40FFFFFF);
        graphics.text(font, label, x, rowY, hovered ? 0xFFFFD27F : 0xFFFFAA00);
        if (hovered) {
            graphics.setTooltipForNextFrame(font, rewardsTooltip(quest, state, progress), lastMouseX, lastMouseY);
        }
    }

    /**
     * The tooltip lines of the rewards row: the rewards, then the reward choice and the failure
     * rewards if the quest has any.
     */
    private List<FormattedCharSequence> rewardsTooltip(Quest quest, QuestState state, QuestProgress progress) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!quest.rewards().isEmpty()) {
            lines.addAll(font.split(coloured(Component.translatable("questapi.gui.rewards"), 0xFFAA00), REWARDS_TOOLTIP_WIDTH));
            for (QuestReward reward : quest.rewards()) {
                lines.addAll(font.split(Component.literal("- ").append(reward.describe()), REWARDS_TOOLTIP_WIDTH));
            }
        }
        Optional<ChoiceBlock> choices = choiceBlock(quest, state, progress);
        if (choices.isPresent()) {
            lines.addAll(font.split(coloured(choices.get().header(), choices.get().headerColor()), REWARDS_TOOLTIP_WIDTH));
            for (Component line : choices.get().lines()) {
                lines.addAll(font.split(line, REWARDS_TOOLTIP_WIDTH));
            }
        }
        List<QuestReward> failureRewards = quest.failureRewards();
        if (!failureRewards.isEmpty()) {
            lines.addAll(font.split(coloured(Component.translatable("questapi.gui.failure_rewards"), 0xFF8888), REWARDS_TOOLTIP_WIDTH));
            for (QuestReward reward : failureRewards) {
                lines.addAll(font.split(Component.literal("- ").append(reward.describe()), REWARDS_TOOLTIP_WIDTH));
            }
        }
        return lines;
    }

    private static Component coloured(Component text, int rgb) {
        return text.copy().withStyle(style -> style.withColor(rgb & 0xFFFFFF));
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
