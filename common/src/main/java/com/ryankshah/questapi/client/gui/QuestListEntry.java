package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * A single row in the {@link QuestListWidget}, showing a quest's icon, title and a coloured state
 * marker (locked/available/active/completed/rewarded).
 */
public final class QuestListEntry extends ObjectSelectionList.Entry<QuestListEntry> {

    private final Quest quest;
    private final QuestState state;

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
        graphics.text(font, quest.title(), textX, y + 3, titleColor);
        graphics.text(font, QuestGuiText.stateLabel(quest, state), textX, y + 3 + 11, QuestGuiText.stateColor(state));
    }
}
