# QuestAPI

A modern, extensible quest framework for Minecraft mods, targeting **Minecraft 26.3** on both
**Fabric** and **NeoForge**. QuestAPI lets other mods define, register, track and reward quests
without caring which loader they're running on, and ships with a polished default quest-book GUI you
can use as-is or replace entirely.

The project is a standard MultiLoader-Template layout:

```
common/     - the API, its default implementation, and the default GUI (loader-agnostic)
fabric/     - Fabric entrypoints, networking registration, platform services
neoforge/   - NeoForge entrypoints, networking registration, platform services
```

All quest logic is **server-authoritative**. The client only ever displays what the server has sent
it and asks the server to perform actions (start a quest, abandon it, deliver items); it never
decides quest state on its own.

## Table of contents

1. [Installing](#installing)
2. [Architecture](#architecture)
3. [Quest lifecycle](#quest-lifecycle)
4. [Registering a quest](#registering-a-quest)
5. [Datapack quests](#datapack-quests)
6. [Objectives](#objectives)
7. [Rewards](#rewards)
8. [Prerequisites / conditions](#prerequisites--conditions)
9. [Repeatable quests](#repeatable-quests)
10. [Custom objectives](#custom-objectives)
11. [Custom rewards and conditions](#custom-rewards-and-conditions)
12. [Listening for quest events](#listening-for-quest-events)
13. [Opening the default GUI](#opening-the-default-gui)
14. [Server/client responsibilities](#serverclient-responsibilities)
15. [Persistence and networking](#persistence-and-networking)
16. [Mixins](#mixins)
17. [Development mode](#development-mode)
18. [Example quests](#example-quests)

## Installing

Published to GitHub Packages, which requires authenticating even to read a public package (a GitHub
Packages limitation, not ours) - use a personal access token with `read:packages` scope:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/ryankshah/questapi')
        credentials {
            username = project.findProperty('gpr.user')
            password = project.findProperty('gpr.token')
        }
    }
}

dependencies {
    implementation 'com.ryankshah.questapi:common:26.3.0.0'
}
```

## Architecture

```
com.ryankshah.questapi
 ├─ QuestApi                 - static entry point: registry(), manager(), bootstrap()
 ├─ api/                     - the public library surface
 │   ├─ QuestRegistry        - static content: quests, categories, objective/reward/condition types
 │   ├─ QuestManager         - server-authoritative runtime: state, lifecycle, persistence access
 │   └─ quest/
 │       ├─ Quest            - immutable quest definition
 │       ├─ QuestCategory    - GUI grouping
 │       ├─ QuestState       - LOCKED / AVAILABLE / ACTIVE / COMPLETED / REWARDED / ABANDONED
 │       ├─ ResetMode        - WALL_CLOCK / IN_GAME_DAY, for repeatable quests
 │       ├─ QuestProgress    - mutable per-player runtime state for one quest
 │       ├─ PlayerQuestData  - a player's QuestProgress map
 │       ├─ QuestContext     - passed to objectives/rewards/conditions when evaluated
 │       ├─ objective/       - ObjectiveDefinition, ObjectiveProgress, ObjectiveType + built-ins
 │       ├─ reward/          - QuestReward, RewardType + built-ins
 │       ├─ condition/       - QuestCondition, ConditionType + built-ins
 │       └─ event/           - QuestEvents / QuestEventListener
 ├─ impl/                    - the default implementation of the above interfaces
 │   ├─ QuestRegistryImpl, QuestManagerImpl, BuiltinContent, DevConfig
 │   ├─ persistence/         - QuestSavedData (server-wide SavedData)
 │   └─ network/             - payload records + server-side packet handling
 ├─ client/                  - client-side cache + the default GUI (QuestScreen)
 ├─ command/                 - dev-only /quests debug commands
 ├─ mixin/                   - the two mixins the API actually needs, and why (see below)
 └─ example/                 - the example quest tree, registered only when dev=true
```

`api` is the contract other mods should code against. `impl` is *a* default implementation of that
contract - if you want to swap persistence or networking strategies you can do so without touching
the public interfaces. The default GUI (`client/gui`) is a consumer of `api`/`client.ClientQuestDataCache`
just like any third-party GUI would be; it has no special access.

## Quest lifecycle

```
LOCKED --------> AVAILABLE --------> ACTIVE --------> COMPLETED --------> REWARDED
  ^  prerequisites     |  startQuest()      |  all objectives      |  claimRewards()
  |  not met yet       |  (or autoActivate) |  reach target        |
  |                    |                    |                      |
  +--------------------+---- resetQuest() / abandonQuest() --------+
                       ^                                           |
                       +---- repeatable quest, cooldown elapsed ---+
```

* **LOCKED** - one or more prerequisites aren't satisfied yet.
* **AVAILABLE** - prerequisites pass, but the player hasn't started the quest.
* **ACTIVE** - started; objective progress is being tracked.
* **COMPLETED** - every objective hit its target; rewards not yet claimed.
* **REWARDED** - rewards claimed. Terminal, unless explicitly reset - or, for a
  [repeatable quest](#repeatable-quests), until its cooldown elapses.
* **ABANDONED** - the player cancelled an active quest.

A quest whose progress has never been touched by a player has *no stored entry at all* - the state
above is computed on demand. Once a quest becomes available it's always materialised, so the GUI can
tell "definitely locked" apart from "just hasn't been checked yet".

## Registering a quest

```java
public class MyMod {
    public static final Identifier FIRST_DIAMOND = Identifier.fromNamespaceAndPath("mymod", "first_diamond");
    public static final Identifier MAIN_CATEGORY = Identifier.fromNamespaceAndPath("mymod", "main");

    // Call from a server-starting hook (Fabric's ServerLifecycleEvents.SERVER_STARTING,
    // NeoForge's ServerStartingEvent) - NOT from onInitialize()/the @Mod constructor. Item data
    // components aren't bound that early, and building an ItemStack icon before they are will
    // throw "Components not bound yet".
    public static void onServerStarting(MinecraftServer server) {
        QuestRegistry registry = QuestApi.registry();

        registry.registerCategory(QuestCategory.of(MAIN_CATEGORY,
                Component.literal("Main Quests"), new ItemStack(Items.BOOK)));

        registry.registerQuest(Quest.builder(FIRST_DIAMOND)
                .title(Component.literal("First Diamond"))
                .description(Component.literal("Every miner's proudest moment."))
                .icon(new ItemStack(Items.DIAMOND))
                .category(MAIN_CATEGORY)
                .objective(new CollectItemObjective(Items.DIAMOND, 1))
                .reward(new ExperienceReward(50))
                .autoActivate(true)
                .build());
    }
}
```

Register your objective/reward/condition *types* (which hold no `ItemStack`s) during common init if
you like, but register actual `Quest`/`QuestCategory` *content* from a server-starting hook, as
above - `registerQuest`/`registerCategory` are safe to call every time the server starts (they just
overwrite the same entry), so this works fine even across repeated singleplayer world switches in one
JVM. Registering quests after the server has fully started is not supported for other reasons -
definitions are synced to clients once, at login.

## Datapack quests

Quests and categories can also be defined entirely in JSON, as an alternative or complement to the
Java API above - useful for modpack/server datapacks that don't want to write a mod. Files live at:

```
data/<namespace>/questapi/categories/<anything>.json
data/<namespace>/questapi/quests/<anything>.json
```

The file *path* is purely organisational (nest folders however you like); the quest/category's real
ID and category assignment come from fields inside the JSON itself. The JSON shape is exactly what
`QuestCategory` and `Quest` (de)serialize to/from - the same `Codec`s used for network sync - so
every built-in objective/reward/condition type, and any third-party ones a loaded mod has registered,
work here too via their own `"type"` field. See
[`common/src/main/resources/data/examplequests/questapi/`](common/src/main/resources/data/examplequests/questapi/)
for the files behind the bundled "JSON Demo" category, reproduced here:

```json
{
  "id": "examplequests:json_quest_demo",
  "category": "examplequests:json_demo",
  "display": {
    "title": { "text": "A Quest From JSON" },
    "description": { "text": "This quest is defined entirely in a datapack JSON file." },
    "icon": { "id": "minecraft:paper", "count": 1 },
    "sort_order": 0
  },
  "lifecycle": { "auto_activate": true },
  "objectives": [
    { "type": "questapi:collect_item", "item": "minecraft:paper", "amount": 5 }
  ],
  "rewards": [
    { "type": "questapi:experience", "points": 15 }
  ],
  "prerequisites": []
}
```

A quest is made of nested blocks, each optional unless noted:

- `display` (required): `title`, `description`, `icon`, `sort_order`.
- `lifecycle`: `auto_activate`, `sequential`, `abandonable` and `repeat` (`reset_mode` optional, `reset_amount`). A quest is repeatable exactly when `repeat` is present. `abandonable` (default `false`) lets the player drop the quest from the quest book: the book shows an Abandon button next to Track only when the quest is abandonable and `allow-manual-abandon` is on in `questapi.properties`, and the server ignores an abandon packet for any other quest. Mods can always call `QuestManager#abandonQuest`. `hide_when_rewarded` (default `false`) keeps the quest out of the quest book's Completed tab once it is rewarded, for repeatable quests that would otherwise fill the tab; it still shows in the Ongoing tab while it is active or ready to claim.
- `failure`: `retryable`, `time_limit` (`amount` and `unit`: `GAME_DAYS`, `GAME_HOURS` or `REAL_SECONDS`), `reason`, `fail_on` (a list of `{ "event": "<event key>", "during_step": <index> }`) and `rewards` (same reward entries as the top-level `rewards`, claimed once after the quest failed; see below).
- `toast_overrides`: `started`, `ready`, `completed` and `failed` replace the title line of the matching toast.
- `reward_choices` (top-level, next to `rewards`): a list of `{ "id", "label", "rewards" }` entries; see "Reward choices" below.

A retryable quest is reset straight back to its starting state when it fails; a non-retry quest stays
`FAILED` permanently, and the `questapi:quest_failed` condition lets other quests depend on that.

**Failure rewards:** `failure.rewards` takes exactly the same entries as `rewards` (any reward type,
including third-party ones) and is claimed through the same `QuestManager#claimRewards` call as a
completed quest's rewards. They are not granted at the moment of failure,
because many failure triggers (such as `questapi:player_died`) fire while the player cannot receive
items; a mod claims them afterwards. The quest stays `FAILED` after claiming, so
`quest_failed` prerequisites keep working, and `QuestProgress#failureRewardsClaimed()` stops a second
claim. `QuestProgress#claimable(quest)` tells whether a claim would currently grant something
(completed, or failed with unclaimed failure rewards). Failure rewards only work on non-retryable
quests, because a retryable quest is reset on failure; the datapack loader warns about that
combination. The quest book lists them in the detail pane under "Rewards if failed".

```json
"failure": {
  "retryable": false,
  "fail_on": [{ "event": "questapi:player_died" }],
  "rewards": [{ "type": "questapi:item", "stack": { "id": "minecraft:bread", "count": 1 } }]
}
```
New quest options go inside the matching block rather than at the top level, because a record codec
holds at most 16 fields.

Loading happens via an ordinary server data reload listener (`QuestDataLoader`), same as recipes/loot
tables/advancements - but unlike those, quest JSON only gets *read* there. Item data components
aren't bound yet during a data reload (the same constraint that affects Java registration - see the
warning in [Registering a quest](#registering-a-quest) - applies here too, just discovered the hard
way: decoding straight into `Quest`/`QuestCategory` inside the reload listener throws "does not have
components yet"). The actual decode and registration happens in `QuestDataLoader#finalizeAndRegister`,
called from the same server-starting hook `ExampleQuests` uses. Datapack-sourced quests/categories are
tracked and removed automatically if their file disappears on the next `/reload`; anything registered
from Java is never touched by this mechanism. The bundled example JSON quest is namespaced under
`examplequests`, same as the Java example tree, and is likewise only loaded when `dev=true` - your
own datapack content is unaffected by that flag and always loads.

## Objectives

Built-in objective types, all under `com.ryankshah.questapi.api.quest.objective.impl`:

| Class                    | What it tracks                                              | Style |
|---------------------------|---------------------------------------------------------------|-------|
| `CollectItemObjective`    | Player currently possesses N of an item                       | poll  |
| `ConsumeItemObjective`    | `Stats.ITEM_USED` delta since the quest started                | poll  |
| `DeliverItemObjective`    | Player has explicitly delivered N of an item via the GUI       | push  |
| `MineBlockObjective`      | `Stats.BLOCK_MINED` delta since the quest started              | poll  |
| `PlaceBlockObjective`     | Blocks placed since the quest started (via a common mixin)     | push  |
| `CraftItemObjective`      | `Stats.ITEM_CRAFTED` delta since the quest started             | poll  |
| `KillEntityObjective`     | `Stats.ENTITY_KILLED` delta since the quest started            | poll  |
| `TameEntityObjective`     | Animals tamed since the quest started (via a common mixin)     | push  |
| `VisitDimensionObjective` | Player is currently in a specific dimension                    | poll  |
| `VisitLocationObjective`  | Player is within a radius of a position in a specific dimension| poll  |
| `FishObjective`           | `Stats.FISH_CAUGHT` delta since the quest started              | poll  |
| `BreedAnimalsObjective`   | `Stats.ANIMALS_BRED` delta since the quest started             | poll  |

"Poll" objectives recompute their absolute progress from live game state roughly once per second
while the quest is active. "Push" objectives only change in response to a specific event key and
otherwise hold their value - see [Custom objectives](#custom-objectives) for how to add your own of
either kind.

## Rewards

Built-in reward types, under `com.ryankshah.questapi.api.quest.reward.impl`:

* `ItemReward` - places an item stack in the player's inventory (drops it if full).
* `ExperienceReward` - grants experience points.
* `CommandReward` - runs a command as the player, with elevated permission and suppressed output.
* `AdvancementReward` - grants a vanilla or datapack advancement, by awarding every one of its
  criteria (a single criterion isn't enough to complete an advancement with several).
* `EffectReward` - applies a potion/mob effect for a given duration and amplifier.

Rewards are granted exactly once per quest, guarded by the `REWARDED` state - `QuestManager.claimRewards`
returns `false` (and grants nothing) if the quest isn't `COMPLETED` or was already claimed, so a
duplicate click, a lost ack, or a server crash right after claiming can never double-grant.

**Rewards are never claimed from the quest book.** The book has no Claim button; the mod that owns
the turn-in (an NPC dialog, a command, ...) calls `QuestManager#claimRewards`.

**Reward choices:** a quest can offer a choice of rewards with a top-level `reward_choices` list next
to `rewards`:

```json
"rewards": [{ "type": "questapi:item", "stack": { "id": "minecraft:bread", "count": 4 } }],
"reward_choices": [
  { "id": "sword", "label": "A sword", "rewards": [{ "type": "questapi:item", "stack": { "id": "minecraft:iron_sword", "count": 1 } }] },
  { "id": "coins", "label": "Coins", "rewards": [{ "type": "questapi:item", "stack": { "id": "minecraft:gold_ingot", "count": 8 } }] }
]
```

The fixed `rewards` are always granted, plus exactly one choice. Choice ids use only `a-z`, `0-9` and
`_`, must be unique, and a quest needs at least two choices (a single choice is a load error; use a
normal reward). Choices apply to the normal claim only, not to `failure.rewards`. Claim with
`QuestManager#claimRewards(player, questId, choiceId)`, which returns `false` for an unknown choice;
the two-argument claim returns `false` for a quest that has choices. The chosen id is saved as
`QuestProgress#chosenReward()` and cleared when the quest resets. The book lists the choices read-only
and shows "Chosen: ..." once the quest is rewarded.

## Prerequisites / conditions

```java
registry.registerQuest(Quest.builder(SECOND_QUEST)
        .requires(new QuestCompletedCondition(FIRST_DIAMOND))
        .requires(new ItemPossessionCondition(Items.IRON_PICKAXE, 1))
        // ...
        .build());
```

Built-in conditions: `QuestCompletedCondition` (chain quests together), `AdvancementCondition`
(require a vanilla or datapack advancement), `ItemPossessionCondition` (require holding an item),
`ExperienceLevelCondition` (require a minimum XP level), `TimeOfDayCondition` (require day or night),
`WeatherCondition` (require clear/rain/thunder), `BiomeCondition` (require standing in a specific
biome).
A quest needs *all* of its conditions to pass to leave `LOCKED` - unless one of them is an
`AnyOfCondition`, which itself passes if *any* of the conditions it wraps pass:

```java
registry.registerQuest(Quest.builder(EITHER_QUEST)
        .requires(new AnyOfCondition(List.of(
                new AdvancementCondition(someAdvancementId),
                new ItemPossessionCondition(Items.DIAMOND_PICKAXE, 1))))
        .build());
```

`AnyOfCondition` is the one built-in condition that isn't a plain `TYPE` constant, since it wraps
other conditions and needs the registry's own dispatch codec to (de)serialize them - register it via
`registry.registerConditionType(AnyOfCondition.type(registry))` (already done for you by this
module's own `BuiltinContent`; only relevant if you're writing your own composite condition type
following the same shape).

## Repeatable quests

```java
registry.registerQuest(Quest.builder(DAILY_QUEST)
        // ... title/description/icon/category/objective/reward as usual
        .repeatable(24)                             // uses the server's default ResetMode
        .build());

registry.registerQuest(Quest.builder(WEEKLY_EVENT)
        .repeatable(ResetMode.IN_GAME_DAY, 7)        // overrides it for this quest only
        .build());
```

A repeatable quest returns from `REWARDED` to `AVAILABLE` (or `LOCKED`, if its prerequisites have
since regressed) once its cooldown elapses, instead of staying `REWARDED` forever. `ResetMode` has
two options:

* `WALL_CLOCK` - resets a fixed number of real hours after the quest was last claimed, checked
  against system time. The check happens the next time `QuestManager` ticks that player, not at the
  exact moment the cooldown expires - it still resets even if the player was offline for it.
* `IN_GAME_DAY` - resets after a fixed number of in-game days, measured against the world's age
  (`ServerLevel#getGameTime() / 24000`) rather than the vanilla day/night clock, so it never jumps
  forward when players sleep. Only advances while the server is actually running.

`repeatable(int amount)` defers to the server's configured default mode (see
[Development mode](#development-mode)); `repeatable(ResetMode, int)` overrides it per quest. Either
way, `amount` means hours under `WALL_CLOCK` and in-game days under `IN_GAME_DAY`.

## Custom objectives

Third-party mods can add their own objective types without touching this module:

```java
public record KillBossObjective(Identifier bossId, int amount) implements ObjectiveDefinition {
    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("mymod", "kill_boss");
    public static final MapCodec<KillBossObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("boss").forGetter(KillBossObjective::bossId),
            Codec.INT.fieldOf("amount").forGetter(KillBossObjective::amount)
    ).apply(i, KillBossObjective::new));
    public static final ObjectiveType<KillBossObjective> TYPE = new ObjectiveType<>(TYPE_ID, CODEC);

    public Identifier typeId() { return TYPE_ID; }
    public Component describe() { return Component.literal("Defeat the boss"); }
    public int targetAmount() { return amount; }
    public ItemStack icon() { return new ItemStack(Items.NETHER_STAR); }

    // Push-based: only reacts to our own event key, dispatched from wherever your mod
    // detects the boss dying (a death event, a custom trigger, anything).
    public int evaluate(QuestContext ctx, ObjectiveProgress progress, Identifier eventKey, int amount) {
        return eventKey.equals(TYPE_ID) ? progress.current() + amount : progress.current();
    }
}

// During common init:
QuestApi.registry().registerObjectiveType(KillBossObjective.TYPE);

// From your own boss-death handler:
QuestApi.manager().pushObjectiveEvent(serverPlayer, KillBossObjective.TYPE_ID, 1);
```

If your objective should instead recompute itself from live state (poll-based, like the built-ins
that read stats or inventory), just ignore `eventKey`/`eventValue` and return the freshly computed
absolute amount every time `evaluate` is called - it's invoked once per second for every active
quest automatically.

## Custom rewards and conditions

Same pattern as objectives: implement the interface, define a `MapCodec`, wrap it in a
`RewardType`/`ConditionType`, and register it via `QuestRegistry.registerRewardType` /
`registerConditionType`. A reward's `grant(QuestContext)` runs exactly once, server-side, when the
player claims a completed quest.

## Listening for quest events

```java
QuestEvents.register(new QuestEventListener() {
    @Override
    public void onQuestCompleted(ServerPlayer player, Quest quest) {
        player.sendSystemMessage(Component.literal("Nice work on " + quest.title().getString() + "!"));
    }
});
```

Available callbacks: `onQuestRegistered`, `onQuestStarted`, `onObjectiveProgressChanged`,
`onObjectiveCompleted`, `onQuestCompleted`, `onRewardClaimed`, `onQuestAbandoned` (instead of `onQuestReset`, when an active quest is abandoned), `onQuestReset`. All fire server-side.

## Opening the default GUI

The default GUI is entirely client-side and reads only from `ClientQuestDataCache`:

```java
// from client code, e.g. a keybind handler or another mod's menu button
com.ryankshah.questapi.client.gui.QuestScreen.open();
```

`open()` asks the server to resync (in case the client's cache is stale) and then opens the screen.
To ship your own GUI instead, just don't call this - build your own screen against `ClientQuestDataCache`
and the `Serverbound*Payload`s in `com.ryankshah.questapi.impl.network.payload` (or your own network
abstraction) and ignore `client.gui` entirely. Nothing else in the API depends on the default GUI.

The default GUI shows each objective's progress as a bar, not just a number, plays a sound and pops
a toast the moment a quest completes, and asks for confirmation before abandoning an active quest.
The sound/toast is driven by its own `ClientboundQuestCompletedPayload`, separate from the progress
sync, and fires regardless of which screen (if any) is open at the time.

## Server/client responsibilities

* **Server**: owns `QuestManager`, `QuestSavedData`, and every state transition. All objective
  evaluation, reward granting and prerequisite checking happens here.
* **Client**: owns `ClientQuestDataCache`, populated exclusively by `ClientboundSyncDefinitionsPayload`
  (sent once at login) and `ClientboundSyncProgressPayload` (sent whenever *this player's* progress
  changes). The client sends `Serverbound*Payload`s to request actions and never mutates its own
  cache in response to anything but a server packet.

## Persistence and networking

Player progress is stored in a single server-wide `QuestSavedData` (a modern, `Codec`-based
`SavedData`/`SavedDataType`), keyed by player UUID, attached to `MinecraftServer#getDataStorage()` -
independent of which dimension a player last stood in, and saved/loaded through vanilla's normal
save cycle. Quest *definitions* are never persisted to disk; they're rebuilt from Java registration
every time the server starts and synced to clients over the network, which also means definitions
can be serialized as data (`QuestCodecs`), a starting point for eventual datapack-driven quests.

Networking uses vanilla `CustomPacketPayload` + `StreamCodec`, registered per-loader
(`PayloadTypeRegistry` on Fabric, `RegisterPayloadHandlersEvent`/`PayloadRegistrar` on NeoForge) but
handled by identical shared logic in `impl.network.QuestNetworking` / `client.network.ClientQuestNetworking`.
Only the player's *own* progress is ever synced (never other players'), and only when it actually
changes - not on a timer.

### HUD quest tracking

The quests pinned to the HUD tracker (at most `PlayerQuestData.MAX_TRACKED`, 3) are stored in the
player's `PlayerQuestData` (`tracked` field) and therefore saved with their progress and synced with
it, so pinned quests survive relogs. The quest book pins and unpins through
`ServerboundToggleTrackQuestPayload`; the server only allows pinning an `ACTIVE` quest and drops
pinned quests automatically once they stop being active. `QuestManager#toggleTracked` exposes the same
operation to other mods. A quest is pinned automatically when it starts if the tracker has a free slot;
set `auto-track-started-quests=false` in `config/questapi.properties` to turn that off.

## Mixins

Exactly two, both in the common module, both because there is no cross-loader vanilla event for the
thing they report:

* `ServerPlayerGameModeMixin` - injects into `ServerPlayerGameMode#useItemOn` to detect successful
  block placement (there's no `Stats` entry or event for "placed a block", unlike mining).
* `TamableAnimalMixin` - injects into `TamableAnimal#tame` to detect taming (again, no event).

Everything else (mining, crafting, kills, inventory, exploration, networking, persistence, commands,
events) uses plain vanilla/loader APIs - stats, `SavedData`, `CustomPacketPayload`, Brigadier,
`ServerTickEvent`/`ServerTickEvents`, etc.

## Development mode

QuestAPI writes `config/questapi.properties` on first run:

```properties
dev=false
repeatable-quest-default-reset-mode=WALL_CLOCK
```

Set `dev=true` and restart to:

* register the [example quest tree](#example-quests);
* register the `/quests reset|unlock|progress <player> <quest> [...]` debug commands.

Production quest content registered by other mods is completely unaffected by this flag either way -
it's purely a switch for this module's own example/debug content.

`repeatable-quest-default-reset-mode` (`WALL_CLOCK` or `IN_GAME_DAY`) is the default a
[repeatable quest](#repeatable-quests) resolves to when it doesn't set its own via
`Quest.Builder#repeatable(ResetMode, int)`.

## Example quests

Registered only when `dev=true`, under `com.ryankshah.questapi.example.ExampleQuests`:

* **Getting Started**: *Getting Wood* (auto-starts, collect objective) &rarr; *Stone Age* (multi-objective:
  collect + craft) &rarr; *Getting an Upgrade* (craft objective) - a full prerequisite chain.
* **Diamonds**: *Shiny!* (single collect objective) &rarr; *Diamond Gear* (craft objective, locked
  until *Shiny!* is claimed, multiple stacked rewards).
* **Combat**: *Monster Hunter* (kill 5 zombies) and *Creeper? Aww Man* (kill 1 creeper), both
  auto-starting kill-entity objectives.
* **Exploration**: *Into the Nether* (visit-dimension objective).
* **Farm & Sea**: *Gone Fishing* (fish objective, repeatable on the server's default schedule) and
  *Animal Husbandry* (breed-animals objective, repeatable every in-game day - a per-quest
  `ResetMode` override).
* **World Events**: *Thunderstruck* (only available during a thunderstorm - `WeatherCondition` and
  `EffectReward`) and *Night Owl* (available at night or in a dark forest - `AnyOfCondition`'s OR
  logic and `AdvancementReward`).
* **JSON Demo**: *A Quest From JSON* - identical in every respect to the quests above, but defined
  entirely in [a datapack JSON file](#datapack-quests) instead of Java code.

Between them the tree exercises every built-in objective type except delivery, every built-in reward
and condition type, both repeatable `ResetMode`s, a three-quest prerequisite chain, a locked quest, a
multi-objective quest, and a quest with multiple rewards - use `/quests progress` and `/quests unlock`
to jump around the tree and see every GUI state (locked, available, active, completed, rewarded)
without playing through it.
