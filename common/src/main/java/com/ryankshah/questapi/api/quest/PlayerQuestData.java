package com.ryankshah.questapi.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All quest progress for a single player, keyed by quest ID.
 * <p>
 * Quests the player has never interacted with are not present in {@link #progress} at all; callers
 * should treat a missing entry as {@link QuestState#LOCKED} (or {@code AVAILABLE} if the quest has no
 * prerequisites), which {@code QuestManager} materialises lazily rather than eagerly creating an
 * entry for every registered quest for every player.
 */
public final class PlayerQuestData {

    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerQuestData::playerId),

            Codec.unboundedMap(Identifier.CODEC, QuestProgress.CODEC).fieldOf("quests").forGetter(PlayerQuestData::progressRaw),

            Identifier.CODEC.listOf().optionalFieldOf("tracked", List.of()).forGetter(PlayerQuestData::tracked)
    ).apply(instance, PlayerQuestData::new));

    /** The most quests that can be pinned to the HUD tracker at once. */
    public static final int MAX_TRACKED = 3;

    private final UUID playerId;
    private final Map<Identifier, QuestProgress> progress;
    private final List<Identifier> tracked;

    public PlayerQuestData(UUID playerId, Map<Identifier, QuestProgress> progress, List<Identifier> tracked) {
        this.playerId = playerId;
        this.progress = new HashMap<>(progress);
        this.tracked = new ArrayList<>(tracked);
    }

    public static PlayerQuestData empty(UUID playerId) {
        return new PlayerQuestData(playerId, Map.of(), List.of());
    }

    /**
     * The quests pinned to the HUD tracker, oldest first, at most {@link #MAX_TRACKED}.
     */
    public List<Identifier> tracked() {
        return tracked;
    }

    public boolean isTracked(Identifier questId) {
        return tracked.contains(questId);
    }

    /**
     * Unpins {@code questId} if it is pinned; otherwise pins it, dropping the oldest pinned quest
     * when the tracker is already full.
     */
    public void toggleTracked(Identifier questId) {
        if (tracked.remove(questId)) {
            return;
        }
        if (tracked.size() >= MAX_TRACKED) {
            tracked.remove(0);
        }
        tracked.add(questId);
    }

    /**
     * Pins {@code questId} only if the tracker has a free slot and it is not already pinned.
     *
     * @return whether the quest was newly pinned
     */
    public boolean trackIfRoom(Identifier questId) {
        if (tracked.size() >= MAX_TRACKED || tracked.contains(questId)) {
            return false;
        }
        tracked.add(questId);
        return true;
    }

    /**
     * Drops every pinned quest that is no longer {@code ACTIVE}, so the saved list never keeps
     * stale entries after a quest completes, fails, is abandoned or reset.
     *
     * @return whether anything was removed
     */
    public boolean pruneTracked() {
        return tracked.removeIf(id -> {
            QuestProgress p = progress.get(id);
            return p == null || p.state() != QuestState.ACTIVE;
        });
    }

    public UUID playerId() {
        return playerId;
    }

    public Map<Identifier, QuestProgress> progress() {
        return progress;
    }

    private Map<Identifier, QuestProgress> progressRaw() {
        return progress;
    }

    public QuestProgress get(Identifier questId) {
        return progress.get(questId);
    }

    public QuestProgress getOrCreate(Identifier questId) {
        return progress.computeIfAbsent(questId, id -> QuestProgress.locked());
    }

    /**
     * Deep copy, safe to hand to something that will read it later (or on another thread) without
     * racing further mutations - e.g. a network payload that gets encoded asynchronously well after
     * the call that queued it returns.
     */
    public PlayerQuestData copy() {
        Map<Identifier, QuestProgress> copiedProgress = new HashMap<>();
        for (Map.Entry<Identifier, QuestProgress> entry : progress.entrySet()) {
            copiedProgress.put(entry.getKey(), entry.getValue().copy());
        }
        return new PlayerQuestData(playerId, copiedProgress, tracked);
    }
}
