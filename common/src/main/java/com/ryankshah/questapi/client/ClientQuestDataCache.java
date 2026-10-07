package com.ryankshah.questapi.client;

import com.ryankshah.questapi.api.quest.ManualQuestActions;
import com.ryankshah.questapi.api.quest.PlayerQuestData;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.QuestFailureRules;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.QuestTimeLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Client-side cache of the last data synced from the server. This is the <em>only</em> source of
 * quest information the default GUI reads from - it never inspects the server's authoritative
 * state directly, since on a real connection it can't.
 * <p>
 * A quest absent from the synced progress map is always displayed as {@code LOCKED}: the server only
 * omits an entry for quests whose prerequisites are not yet satisfied.
 */
public final class ClientQuestDataCache {

    private static final int DEFAULT_TICKS_PER_GAME_DAY = 24000;

    public static final ClientQuestDataCache INSTANCE = new ClientQuestDataCache();

    private List<QuestCategory> categories = List.of();
    private List<Quest> quests = List.of();
    private ManualQuestActions manualActions = ManualQuestActions.ALL;
    private int ticksPerGameDay = DEFAULT_TICKS_PER_GAME_DAY;
    private PlayerQuestData progress;
    private int revision = 0;
    private boolean clockStamped = false;
    private long syncedClockTime = 0L;
    private long syncedGameTime = 0L;

    private ClientQuestDataCache() {
    }

    /**
     * Bumped every time new data arrives from the server. The default GUI polls this once per tick
     * to know when to refresh, instead of needing its own network callback plumbing.
     */
    public int revision() {
        return revision;
    }

    public void setDefinitions(List<QuestCategory> categories, List<Quest> quests, ManualQuestActions manualActions, int ticksPerGameDay) {
        this.categories = categories;
        this.quests = quests;
        this.manualActions = manualActions;
        this.ticksPerGameDay = ticksPerGameDay;
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
        stampClocks();
        this.revision++;
    }

    public void clear() {
        this.categories = List.of();
        this.quests = List.of();
        this.manualActions = ManualQuestActions.ALL;
        this.ticksPerGameDay = DEFAULT_TICKS_PER_GAME_DAY;
        this.progress = null;
        this.clockStamped = false;
        this.revision++;
    }

    /**
     * Remembers the client's clocks at the moment progress arrived, so a quest's time limit can be
     * counted down locally from the elapsed time the server sent along with it.
     */
    private void stampClocks() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            clockStamped = false;
            return;
        }
        syncedClockTime = level.getOverworldClockTime();
        syncedGameTime = level.getGameTime();
        clockStamped = true;
    }

    /**
     * How many ticks of its own clock an active quest has left before its time limit fails it, or
     * empty if the quest has no time limit, is not active, or no clock reading is available yet.
     */
    public OptionalLong remainingLimitTicks(Quest quest) {
        QuestTimeLimit limit = quest.failure().flatMap(QuestFailureRules::timeLimit).orElse(null);
        Level level = Minecraft.getInstance().level;
        if (limit == null || level == null || !clockStamped || getState(quest.id()) != QuestState.ACTIVE) {
            return OptionalLong.empty();
        }
        long sinceSync = limit.unit().usesDayClock()
                ? level.getOverworldClockTime() - syncedClockTime
                : level.getGameTime() - syncedGameTime;
        long total = limit.unit().toTicks(limit.amount(), ticksPerGameDay);
        return OptionalLong.of(Math.max(0L, total - getProgress(quest.id()).elapsedTicks() - Math.max(0L, sinceSync)));
    }

    /**
     * The length of an in-game day in ticks as configured on the server, for formatting day-clock
     * time limits.
     */
    public int ticksPerGameDay() {
        return ticksPerGameDay;
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
     * The quests currently pinned to the in-game HUD tracker, oldest first, at most
     * {@link PlayerQuestData#MAX_TRACKED}. The list is owned and saved by the server and arrives
     * with the progress sync; pin or unpin through {@code ClientQuestNetworking#requestToggleTrackQuest}.
     */
    public List<Identifier> trackedQuestIds() {
        return progress == null ? List.of() : List.copyOf(progress.tracked());
    }

    public boolean isTracked(Identifier questId) {
        return progress != null && progress.isTracked(questId);
    }
}
