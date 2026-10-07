package com.ryankshah.questapi.client.gui;

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
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Renders the player's pinned quests (see {@link ClientQuestDataCache#toggleTracked}), at most
 * {@link ClientQuestDataCache#MAX_TRACKED}, stacked in the top right corner of the HUD, so their
 * objectives stay visible without reopening the quest book.
 * Registered as a HUD element/layer by each loader's client bootstrap; identical on both since
 * Fabric's {@code HudElement} and NeoForge's {@code GuiLayer} share this exact render signature.
 */
public final class QuestTrackerHud {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 3;
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
        List<List<FormattedCharSequence>> objectiveLines = new ArrayList<>();
        List<Integer> shownObjectives = new ArrayList<>();
        List<ObjectiveDefinition> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!progress.objectiveUnlocked(quest, i)) {
                break;
            }
            ObjectiveDefinition objective = objectives.get(i);
            ObjectiveProgress op = progress.objectives().getOrDefault(i, ObjectiveProgress.empty());
            String amountText = " (" + op.current() + "/" + objective.targetAmount() + ")";
            Component line = objective.describe().copy().append(Component.literal(amountText));
            objectiveLines.add(font.split(line, WIDTH));
            shownObjectives.add(i);
        }

        int contentHeight = titleLines.size() * LINE_HEIGHT + timeLines.size() * LINE_HEIGHT + 2;
        for (List<FormattedCharSequence> lines : objectiveLines) {
            contentHeight += lines.size() * LINE_HEIGHT + BAR_HEIGHT + 2;
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

        for (int shown = 0; shown < shownObjectives.size(); shown++) {
            int i = shownObjectives.get(shown);
            ObjectiveDefinition objective = objectives.get(i);
            ObjectiveProgress op = progress.objectives().getOrDefault(i, ObjectiveProgress.empty());
            int color = op.complete() ? 0xFF55FF55 : 0xFFDDDDDD;
            for (FormattedCharSequence line : objectiveLines.get(shown)) {
                graphics.text(font, line, x, cursorY, color);
                cursorY += LINE_HEIGHT;
            }

            int target = objective.targetAmount();
            float ratio = target > 0 ? Math.min(1f, (float) op.current() / target) : 0f;
            int filledWidth = Math.round(WIDTH * ratio);
            graphics.fill(x, cursorY, x + WIDTH, cursorY + BAR_HEIGHT, 0x60000000);
            if (filledWidth > 0) {
                graphics.fill(x, cursorY, x + filledWidth, cursorY + BAR_HEIGHT, op.complete() ? 0xFF55FF55 : 0xFF55FFFF);
            }
            cursorY += BAR_HEIGHT + 2;
        }
        return cursorY;
    }
}
