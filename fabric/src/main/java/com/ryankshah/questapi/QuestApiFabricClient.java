package com.ryankshah.questapi;

import com.mojang.blaze3d.platform.InputConstants;
import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.client.gui.QuestScreen;
import com.ryankshah.questapi.client.gui.QuestTrackerHud;
import com.ryankshah.questapi.client.network.ClientQuestNetworking;
import com.ryankshah.questapi.impl.network.payload.ClientboundChapterEndingPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundObjectiveCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestCompletedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestFailedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestRewardedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestStartedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundQuestUnlockedPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncDefinitionsPayload;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncProgressPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class QuestApiFabricClient implements ClientModInitializer {

    private static KeyMapping openQuestsKey;

    @Override
    public void onInitializeClient() {
        openQuestsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.questapi.open_quests", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, KeyMapping.Category.MISC));

        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncDefinitionsPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleSyncDefinitions(payload));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncProgressPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleSyncProgress(payload.data()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundQuestCompletedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleQuestCompleted(payload.toastTitle(), payload.questTitle()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundQuestUnlockedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleQuestUnlocked(payload.questTitle()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundQuestStartedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleQuestStarted(payload.toastTitle(), payload.questTitle()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundQuestRewardedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleQuestRewarded(payload.toastTitle(), payload.questTitle()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundQuestFailedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleQuestFailed(payload.toastTitle(), payload.questTitle()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundObjectiveCompletedPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleObjectiveCompleted(payload.objectiveDescription()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChapterEndingPayload.TYPE,
                (payload, context) -> ClientQuestNetworking.handleChapterEnding(payload.title(), payload.message()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openQuestsKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    QuestScreen.open();
                }
            }
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(QuestApi.MOD_ID, "quest_tracker"), QuestTrackerHud::render);
    }
}
