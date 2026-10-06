package com.ryankshah.questapi.client;

import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Client-side cache of the last data synced from the server. This is the <em>only</em> source of
 * quest information the default GUI reads from - it never inspects the server's authoritative
 * state directly, since on a real connection it can't.
 * <p>
 * A quest absent from the synced progress map is always displayed as {@code LOCKED}: the server only
 * omits an entry for quests whose prerequisites are not yet satisfied.
 */
public final class ClientQuestDataCache {

    public static final ClientQuestDataCache INSTANCE = new ClientQuestDataCache();

    private List<QuestCategory> categories = List.of();
    private List<Quest> quests = List.of();
    private ManualQuestActions manualActions = ManualQuestActions.ALL;
    private PlayerQuestData progress;
    private int revision = 0;
    private Identifier trackedQuestId;

    private ClientQuestDataCache() {
    }

    /**
     * Bumped every time new data arrives from the server. The default GUI polls this once per tick
     * to know when to refresh, instead of needing its own network callback plumbing.
     */
    public int revision() {
        return revision;
    }

    public void setDefinitions(List<QuestCategory> categories, List<Quest> quests, ManualQuestActions manualActions) {
        this.categories = categories;
        this.quests = quests;
        this.manualActions = manualActions;
        this.revision++;
    }

    /**
     * Which book actions the server currently allows. Defaults to everything allowed until the
     * first definitions sync arrives.
     */
    public ManualQuestActions manualActions() {
        return manualActions;
    }

    public void setProgress(PlayerQuestData progress) {
        this.progress = progress;
        this.revision++;
    }

    public void clear() {
        this.categories = List.of();
        this.quests = List.of();
        this.manualActions = ManualQuestActions.ALL;
        this.progress = null;
        this.revision++;
    }

    public List<QuestCategory> categories() {
        return categories;
    }

    public List<Quest> quests() {
        return quests;
    }

    public List<Quest> questsInCategory(Identifier categoryId) {
        return quests.stream().filter(q -> q.categoryId().equals(categoryId)).sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder())).toList();
    }

    public Optional<Quest> getQuest(Identifier id) {
        return quests.stream().filter(q -> q.id().equals(id)).findFirst();
    }

    public QuestState getState(Identifier questId) {
        if (progress == null) {
            return QuestState.LOCKED;
        }
        QuestProgress p = progress.get(questId);
        return p != null ? p.state() : QuestState.LOCKED;
    }

    public QuestProgress getProgress(Identifier questId) {
        if (progress == null) {
            return QuestProgress.locked();
        }
        QuestProgress p = progress.get(questId);
        return p != null ? p : QuestProgress.locked();
    }

    public boolean hasData() {
        return progress != null;
    }

    /**
     * The quest currently pinned to the in-game HUD tracker, or {@code null} if none is pinned.
     * Purely a client-side display preference - never synced to the server or other clients.
     */
    public Identifier trackedQuestId() {
        return trackedQuestId;
    }

    /**
     * Pins {@code questId} to the HUD tracker, or unpins it if it's already the tracked quest.
     */
    public void toggleTracked(Identifier questId) {
        this.trackedQuestId = questId.equals(trackedQuestId) ? null : questId;
    }
}
