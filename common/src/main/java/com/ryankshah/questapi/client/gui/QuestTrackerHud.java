package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.ObjectiveEntry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.client.ClientQuestDataCache;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Renders the player's pinned quests (see {@link ClientQuestDataCache#trackedQuestIds}), at most
 * {@link com.ryankshah.questapi.api.quest.PlayerQuestData#MAX_TRACKED}, stacked in the top right
 * corner of the HUD, so their objectives stay visible without reopening the quest book. The pinned
 * list is saved by the server, so it survives relogs.
 * Registered as a HUD element/layer by each loader's client bootstrap; identical on both since
 * Fabric's {@code HudElement} and NeoForge's {@code GuiLayer} share this exact render signature.
 */
public final class QuestTrackerHud {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 3;
    private static final int GROUP_INDENT = 8;
    private static final int WIDTH = 140;
    private static final int BLOCK_GAP = 8;
    private static final float HUD_SCALE = 0.75f;

    private QuestTrackerHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }
        ClientQuestDataCache cache = ClientQuestDataCache.INSTANCE;
        List<Identifier> trackedIds = cache.trackedQuestIds();
        if (trackedIds.isEmpty()) {
            return;
        }

        int x = Math.round(minecraft.getWindow().getGuiScaledWidth() / HUD_SCALE) - WIDTH - MARGIN;
        int y = MARGIN;

        graphics.pose().pushMatrix();
        graphics.pose().scale(HUD_SCALE, HUD_SCALE);
        try {
            for (Identifier trackedId : trackedIds) {
                Quest quest = cache.getQuest(trackedId).orElse(null);
                if (quest == null || cache.getState(trackedId) != QuestState.ACTIVE) {
                    continue;
                }
                y = renderQuest(graphics, minecraft.font, cache, quest, x, y) + BLOCK_GAP;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Draws one tracked quest's block at ({@code x}, {@code y}) and returns the Y just below it.
     */
    private static int renderQuest(GuiGraphicsExtractor graphics, Font font, ClientQuestDataCache cache, Quest quest, int x, int y) {
        QuestProgress progress = cache.getProgress(quest.id());

        List<FormattedCharSequence> titleLines = font.split(quest.title(), WIDTH);
        OptionalLong remainingTicks = cache.remainingLimitTicks(quest);
        List<FormattedCharSequence> timeLines = remainingTicks.isPresent()
                ? font.split(QuestGuiText.timeRemainingLabel(quest, remainingTicks.getAsLong(), cache.ticksPerGameDay()), WIDTH)
                : List.of();
        List<Row> rows = new ArrayList<>();
        List<ObjectiveEntry> entries = quest.objectiveEntries();
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            ObjectiveEntry entry = entries.get(entryIndex);
            int first = quest.firstObjectiveOf(entryIndex);
            if (!progress.objectiveUnlocked(quest, first)) {
                // Entries are ordered by step, so everything from here on is not open yet.
                break;
            }
            if (!entry.isGroup()) {
                rows.add(objectiveRow(font, quest, progress, first, 0, entry.optional()));
                continue;
            }
            Component header = entry.required() == 1
                    ? Component.translatable("questapi.gui.objective.choose_one")
                    : Component.translatable("questapi.gui.objective.choose_n", entry.required());
            if (entry.optional()) {
                header = header.copy().append(Component.literal(" ")).append(Component.translatable("questapi.gui.objective.optional"));
            }
            rows.add(new Row(-1, 0, font.split(header, WIDTH),
                    progress.entryComplete(quest, entryIndex) ? 0xFF55FF55 : 0xFFAAAAAA));
            for (int option = 0; option < entry.options().size(); option++) {
                rows.add(objectiveRow(font, quest, progress, first + option, GROUP_INDENT, false));
            }
        }

        int contentHeight = titleLines.size() * LINE_HEIGHT + timeLines.size() * LINE_HEIGHT + 2;
        for (Row row : rows) {
            contentHeight += row.lines().size() * LINE_HEIGHT + (row.isHeader() ? 0 : BAR_HEIGHT + 2);
        }

        graphics.fill(x - 4, y - 3, x + WIDTH + 4, y + contentHeight + 3, 0x90202020);

        int cursorY = y;
        for (FormattedCharSequence line : titleLines) {
            graphics.text(font, line, x, cursorY, 0xFFFFD83C);
            cursorY += LINE_HEIGHT;
        }
        for (FormattedCharSequence line : timeLines) {
            graphics.text(font, line, x, cursorY, 0xFFFFAA00);
            cursorY += LINE_HEIGHT;
        }
        cursorY += 2;

        for (Row row : rows) {
            int rowX = x + row.indent();
            int rowWidth = WIDTH - row.indent();
            for (FormattedCharSequence line : row.lines()) {
                graphics.text(font, line, rowX, cursorY, row.color());
                cursorY += LINE_HEIGHT;
            }
            if (row.isHeader()) {
                continue;
            }

            ObjectiveDefinition objective = quest.objectives().get(row.objectiveIndex());
            ObjectiveProgress op = progress.objectives().getOrDefault(row.objectiveIndex(), ObjectiveProgress.empty());
            int target = objective.targetAmount();
            float ratio = target > 0 ? Math.min(1f, (float) op.current() / target) : 0f;
            int filledWidth = Math.round(rowWidth * ratio);
            graphics.fill(rowX, cursorY, rowX + rowWidth, cursorY + BAR_HEIGHT, 0x60000000);
            if (filledWidth > 0) {
                int barColor = op.complete() ? 0xFF55FF55 : progress.objectiveFrozen(quest, row.objectiveIndex()) ? 0xFF555555 : 0xFF55FFFF;
                graphics.fill(rowX, cursorY, rowX + filledWidth, cursorY + BAR_HEIGHT, barColor);
            }
            cursorY += BAR_HEIGHT + 2;
        }
        return cursorY;
    }

    /**
     * One drawn block: a group header ({@code objectiveIndex} -1, no bar) or one objective with its
     * progress bar. Unfinished options of a group that is already done are greyed out.
     */
    private record Row(int objectiveIndex, int indent, List<FormattedCharSequence> lines, int color) {
        boolean isHeader() {
            return objectiveIndex < 0;
        }
    }

    private static Row objectiveRow(Font font, Quest quest, QuestProgress progress, int index, int indent, boolean optionalTag) {
        ObjectiveDefinition objective = quest.objectives().get(index);
        ObjectiveProgress op = progress.objectives().getOrDefault(index, ObjectiveProgress.empty());
        MutableComponent line = optionalTag
                ? Component.translatable("questapi.gui.objective.optional").append(Component.literal(" "))
                : Component.empty();
        line.append(objective.describe()).append(Component.literal(" (" + op.current() + "/" + objective.targetAmount() + ")"));
        int color = op.complete() ? 0xFF55FF55 : progress.objectiveFrozen(quest, index) ? 0xFF666666 : 0xFFDDDDDD;
        return new Row(index, indent, font.split(line, WIDTH - indent), color);
    }
}
