package com.ryankshah.questapi.client.gui;

import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestState;
import net.minecraft.network.chat.Component;

/**
 * Shared text helpers for the default GUI. Kept separate from rendering code so translation keys
 * live in one obvious place.
 */
public final class QuestGuiText {

    private QuestGuiText() {
    }

    public static Component stateLabel(Quest quest, QuestState state) {
        return switch (state) {
            case LOCKED -> Component.translatable("questapi.gui.state.locked");
            case AVAILABLE -> Component.translatable("questapi.gui.state.available");
            case ACTIVE -> Component.translatable("questapi.gui.state.active");
            case COMPLETED -> Component.translatable("questapi.gui.state.completed");
            case REWARDED -> quest.repeatable()
                    ? Component.translatable("questapi.gui.state.rewarded.repeatable")
                    : Component.translatable("questapi.gui.state.rewarded");
            case ABANDONED -> Component.translatable("questapi.gui.state.abandoned");
            case FAILED -> Component.translatable("questapi.gui.state.failed");
        };
    }

    /**
     * Consistent colour for a quest's state, used by both the quest list rows and the detail panel.
     */
    public static int stateColor(QuestState state) {
        return switch (state) {
            case LOCKED, ABANDONED -> 0xFF808080;
            case AVAILABLE -> 0xFFFFFFFF;
            case ACTIVE -> 0xFFFFD83C;
            case COMPLETED -> 0xFFFFAA00;
            case REWARDED -> 0xFF55C4FF;
            case FAILED -> 0xFFFF5555;
        };
    }
}
