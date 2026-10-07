package com.ryankshah.questapi.client.network;

import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.client.ClientQuestDataCache;
import com.ryankshah.questapi.impl.network.payload.ClientboundSyncDefinitionsPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundAbandonQuestPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundClaimRewardPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundDeliverItemsPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundRequestSyncPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundStartQuestPayload;
import com.ryankshah.questapi.impl.network.payload.ServerboundToggleTrackQuestPayload;
import com.ryankshah.questapi.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-side packet handling logic and outgoing request helpers, shared by both loaders. Loader
 * modules register these methods as the handler bodies for their own client payload registration.
 */
public final class ClientQuestNetworking {

    private ClientQuestNetworking() {
    }

    public static void handleSyncDefinitions(ClientboundSyncDefinitionsPayload payload) {
        ClientQuestDataCache.INSTANCE.setDefinitions(payload.categories(), payload.quests(), payload.manualActions(), payload.ticksPerGameDay());
    }

    public static void handleSyncProgress(PlayerQuestData data) {
        ClientQuestDataCache.INSTANCE.setProgress(data);
    }

    public static void handleQuestCompleted(Component toastTitle, Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                toastTitle, questTitle);
    }

    public static void handleQuestRewarded(Component toastTitle, Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                toastTitle, questTitle);
    }

    public static void handleQuestFailed(Component toastTitle, Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.6F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                toastTitle, questTitle);
    }

    public static void handleObjectiveCompleted(Component objectiveDescription) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("questapi.toast.objective_completed.title"), objectiveDescription);
    }

    public static void handleQuestUnlocked(Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("questapi.toast.quest_unlocked.title"), questTitle);
    }

    public static void handleQuestStarted(Component toastTitle, Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                toastTitle, questTitle);
    }

    public static void requestSync() {
        Services.NETWORK.sendToServer(new ServerboundRequestSyncPayload());
    }

    public static void requestStartQuest(Identifier questId) {
        Services.NETWORK.sendToServer(new ServerboundStartQuestPayload(questId));
    }

    public static void requestToggleTrackQuest(Identifier questId) {
        Services.NETWORK.sendToServer(new ServerboundToggleTrackQuestPayload(questId));
    }

    public static void requestAbandonQuest(Identifier questId) {
        Services.NETWORK.sendToServer(new ServerboundAbandonQuestPayload(questId));
    }

    public static void requestClaimReward(Identifier questId) {
        Services.NETWORK.sendToServer(new ServerboundClaimRewardPayload(questId));
    }

    public static void requestDeliverItems(Identifier questId, int objectiveIndex, int amount) {
        Services.NETWORK.sendToServer(new ServerboundDeliverItemsPayload(questId, objectiveIndex, amount));
    }
}
