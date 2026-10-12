package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * A single row in the {@link QuestListWidget}, showing a quest's icon, title and a coloured state
 * marker (locked/available/active/completed/rewarded).
 */
public final class QuestListEntry extends ObjectSelectionList.Entry<QuestListEntry> {

    private static final String ELLIPSIS = "...";
    /** Space kept free at the right edge of the row. */
    private static final int TEXT_RIGHT_PADDING = 4;
    private static final int SCROLL_PIXELS_PER_SECOND = 30;
    private static final long SCROLL_PAUSE_MILLIS = 700L;

    private final Quest quest;
    private final QuestState state;
    /** When the current hover began, or -1 while the row is not hovered. */
    private long hoverStartMillis = -1L;

    public QuestListEntry(Quest quest, QuestState state) {
        this.quest = quest;
        this.state = state;
    }

    public Quest quest() {
        return quest;
    }

    @Override
    public Component getNarration() {
        return quest.title();
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        if (hovered) {
            graphics.fill(x, y, x + width, y + height, 0x40FFFFFF);
        }

        boolean locked = state == QuestState.LOCKED || state == QuestState.PERMANENTLY_LOCKED;

        int iconX = x + 3;
        int iconY = y + (height - 16) / 2;
        graphics.item(quest.icon(), iconX, iconY);
        if (locked) {
            graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xA0000000);
        }

        int textX = iconX + 22;
        int titleColor = locked ? 0xFF808080 : 0xFFFFFFFF;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int titleX = textX;
        Component levelTag = QuestGuiText.levelTag(quest);
        if (levelTag != null) {
            graphics.text(font, levelTag, textX, y + 3, 0xFFFFFFFF);
            titleX += font.width(levelTag) + 3;
        }
        int titleWidth = Math.max(0, x + width - TEXT_RIGHT_PADDING - titleX);
        drawTitle(graphics, font, titleX, y + 3, titleWidth, titleColor, hovered);
        graphics.text(font, QuestGuiText.stateLabel(quest, state), textX, y + 3 + 11, QuestGuiText.stateColor(state));
    }

    /**
     * Draws the title inside {@code maxWidth} pixels. A title that fits is drawn as it is. A longer
     * one ends in "..." so it never runs past the row, and while the row is hovered it scrolls to
     * its end, pauses and starts over, so the whole title can be read.
     */
    private void drawTitle(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth, int color, boolean hovered) {
        String title = quest.title().getString();
        if (font.width(title) <= maxWidth) {
            hoverStartMillis = -1L;
            graphics.text(font, quest.title(), x, y, color);
            return;
        }
        String shown;
        if (hovered) {
            long now = System.currentTimeMillis();
            if (hoverStartMillis < 0L) {
                hoverStartMillis = now;
            }
            shown = scrolledTitle(font, title, maxWidth, now - hoverStartMillis);
        } else {
            hoverStartMillis = -1L;
            shown = font.plainSubstrByWidth(title, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
        }
        graphics.text(font, Component.literal(shown).withStyle(quest.title().getStyle()), x, y, color);
    }

    /**
     * The part of {@code title} that is visible {@code elapsedMillis} after the hover started: it
     * waits at the start, scrolls to the end at {@link #SCROLL_PIXELS_PER_SECOND}, waits there, and
     * then starts over. Scrolling moves one character at a time.
     */
    private static String scrolledTitle(Font font, String title, int maxWidth, long elapsedMillis) {
        int overflow = font.width(title) - maxWidth;
        long scrollMillis = Math.max(1L, overflow * 1000L / SCROLL_PIXELS_PER_SECOND);
        long cycle = scrollMillis + 2L * SCROLL_PAUSE_MILLIS;
        long phase = elapsedMillis % cycle - SCROLL_PAUSE_MILLIS;
        if (phase <= 0L) {
            return font.plainSubstrByWidth(title, maxWidth);
        }
        if (phase >= scrollMillis) {
            return font.plainSubstrByWidth(title, maxWidth, true);
        }
        int offset = (int) (overflow * phase / scrollMillis);
        int start = 0;
        while (start < title.length() - 1 && font.width(title.substring(0, start + 1)) <= offset) {
            start++;
        }
        return font.plainSubstrByWidth(title.substring(start), maxWidth);
    }
}
