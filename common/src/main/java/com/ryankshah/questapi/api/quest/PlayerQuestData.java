package com.ryankshah.questapi.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All quest progress for a single player, keyed by quest ID.
 * <p>
 * Only quests the player has started (ACTIVE, COMPLETED, REWARDED, FAILED) are present in
 * {@link #progress}. Callers should treat a missing entry as LOCKED, AVAILABLE or PERMANENTLY_LOCKED
 * depending on the player's current conditions, which {@code QuestManager#getState} computes on
 * demand rather than storing an entry for every registered quest for every player.
 */
public final class PlayerQuestData {

    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerQuestData::playerId),

            Codec.unboundedMap(Identifier.CODEC, QuestProgress.CODEC).fieldOf("quests").forGetter(PlayerQuestData::progressRaw),

            Identifier.CODEC.listOf().optionalFieldOf("tracked", List.of()).forGetter(PlayerQuestData::tracked),

            Identifier.CODEC.listOf().optionalFieldOf("unlockSeen", List.of()).forGetter(PlayerQuestData::unlockSeenList)
    ).apply(instance, PlayerQuestData::new));

    /** The most quests that can be pinned to the HUD tracker at once. */
    public static final int MAX_TRACKED = 3;

    private final UUID playerId;
    private final Map<Identifier, QuestProgress> progress;
    private final List<Identifier> tracked;
    private final Set<Identifier> unlockSeen;

    public PlayerQuestData(UUID playerId, Map<Identifier, QuestProgress> progress, List<Identifier> tracked, List<Identifier> unlockSeen) {
        this.playerId = playerId;
        this.progress = new HashMap<>(progress);
        this.tracked = new ArrayList<>(tracked);
        this.unlockSeen = new HashSet<>(unlockSeen);
    }

    public static PlayerQuestData empty(UUID playerId) {
        return new PlayerQuestData(playerId, Map.of(), List.of(), List.of());
    }

    /**
     * Records that the player has been told {@code questId} unlocked, so the unlock toast and
     * {@code onQuestUnlocked} fire only the first time even though availability is computed live and
     * can flip back and forth.
     *
     * @return whether this was the first time
     */
    public boolean markUnlockSeen(Identifier questId) {
        return unlockSeen.add(questId);
    }

    public boolean hasSeenUnlock(Identifier questId) {
        return unlockSeen.contains(questId);
    }

    private List<Identifier> unlockSeenList() {
        return List.copyOf(unlockSeen);
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
        return new PlayerQuestData(playerId, copiedProgress, tracked, List.copyOf(unlockSeen));
    }
}
