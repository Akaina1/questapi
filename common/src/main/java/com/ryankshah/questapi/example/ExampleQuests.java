package com.ryankshah.questapi.example;

import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestCategory;
import com.ryankshah.questapi.api.quest.ResetMode;
import com.ryankshah.questapi.api.quest.condition.impl.AnyOfCondition;
import com.ryankshah.questapi.api.quest.condition.impl.BiomeCondition;
import com.ryankshah.questapi.api.quest.condition.impl.QuestCompletedCondition;
import com.ryankshah.questapi.api.quest.condition.impl.TimeOfDayCondition;
import com.ryankshah.questapi.api.quest.condition.impl.WeatherCondition;
import com.ryankshah.questapi.api.quest.objective.impl.BreedAnimalsObjective;
import com.ryankshah.questapi.api.quest.objective.impl.CollectItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.CraftItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.FishObjective;
import com.ryankshah.questapi.api.quest.objective.impl.KillEntityObjective;
import com.ryankshah.questapi.api.quest.objective.impl.VisitDimensionObjective;
import com.ryankshah.questapi.api.quest.reward.impl.AdvancementReward;
import com.ryankshah.questapi.api.quest.reward.impl.EffectReward;
import com.ryankshah.questapi.api.quest.reward.impl.ExperienceReward;
import com.ryankshah.questapi.api.quest.reward.impl.ItemReward;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;

/**
 * Example quest tree registered only when {@link com.ryankshah.questapi.impl.DevConfig#isDevMode()}
 * is {@code true}. Exists purely to exercise the API and give the default GUI something realistic
 * to display; production quest content from other mods never depends on this class.
 */
public final class ExampleQuests {

    /**
     * Namespace shared by every piece of bundled example content, Java-registered or
     * datapack-JSON-sourced alike, so {@link com.ryankshah.questapi.impl.data.QuestDataLoader} can
     * apply the same dev-mode gate to the bundled example JSON quest as this class applies to itself.
     */
    public static final String MOD = "examplequests";

    public static final Identifier CATEGORY_GETTING_STARTED = id("getting_started");
    public static final Identifier CATEGORY_DIAMONDS = id("diamonds");
    public static final Identifier CATEGORY_COMBAT = id("combat");
    public static final Identifier CATEGORY_EXPLORATION = id("exploration");
    public static final Identifier CATEGORY_FARM_AND_SEA = id("farm_and_sea");
    public static final Identifier CATEGORY_WORLD_EVENTS = id("world_events");

    private ExampleQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD, path);
    }

    private static boolean registered = false;

    /**
     * Registers the example quest tree. Must be called after game registries are fully bound
     * (e.g. from a server-starting hook), not from a mod's common init entrypoint - this quest tree
     * builds {@code ItemStack} icons and rewards eagerly, and item data components aren't bound yet
     * that early in startup. Idempotent, since server-starting can fire more than once per JVM
     * (e.g. singleplayer world switches).
     */
    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        QuestRegistry registry = QuestApi.registry();
        QuestApi.LOG.info("Registering example quests (dev mode)");

        registry.registerCategory(QuestCategory.of(CATEGORY_GETTING_STARTED,
                Component.literal("Getting Started"), new ItemStack(Items.WOODEN_PICKAXE)).withSortOrder(0));
        registry.registerCategory(QuestCategory.of(CATEGORY_DIAMONDS,
                Component.literal("Diamonds"), new ItemStack(Items.DIAMOND)).withSortOrder(1));
        registry.registerCategory(QuestCategory.of(CATEGORY_COMBAT,
                Component.literal("Combat"), new ItemStack(Items.IRON_SWORD)).withSortOrder(2));
        registry.registerCategory(QuestCategory.of(CATEGORY_EXPLORATION,
                Component.literal("Exploration"), new ItemStack(Items.COMPASS)).withSortOrder(3));
        registry.registerCategory(QuestCategory.of(CATEGORY_FARM_AND_SEA,
                Component.literal("Farm & Sea"), new ItemStack(Items.FISHING_ROD)).withSortOrder(4));
        registry.registerCategory(QuestCategory.of(CATEGORY_WORLD_EVENTS,
                Component.literal("World Events"), new ItemStack(Items.CLOCK)).withSortOrder(5));

        Identifier gettingWood = id("getting_wood");
        Identifier stoneAge = id("stone_age");
        Identifier gettingAnUpgrade = id("getting_an_upgrade");
        Identifier shiny = id("shiny");
        Identifier diamondGear = id("diamond_gear");
        Identifier monsterHunter = id("monster_hunter");
        Identifier creeperAwwMan = id("creeper_aww_man");
        Identifier intoTheNether = id("into_the_nether");
        Identifier goneFishing = id("gone_fishing");
        Identifier animalHusbandry = id("animal_husbandry");
        Identifier thunderstruck = id("thunderstruck");
        Identifier nightOwl = id("night_owl");

        // Getting Wood: the entry point of the tree, starts automatically for every player.
        registry.registerQuest(Quest.builder(gettingWood)
                .title(Component.literal("Getting Wood"))
                .description(Component.literal("Every great adventure starts with a tree. Chop down 16 oak logs."))
                .icon(new ItemStack(Items.OAK_LOG))
                .category(CATEGORY_GETTING_STARTED)
                .objective(new CollectItemObjective(Items.OAK_LOG, 16))
                .reward(new ItemReward(new ItemStack(Items.OAK_PLANKS, 8)))
                .reward(new ExperienceReward(10))
                .autoActivate(true)
                .sortOrder(0)
                .build());

        // Stone Age: prerequisite chain step 1, multi-objective (collect cobblestone AND obtain a torch).
        registry.registerQuest(Quest.builder(stoneAge)
                .title(Component.literal("Stone Age"))
                .description(Component.literal("Mine cobblestone and craft yourself some light."))
                .icon(new ItemStack(Items.STONE_PICKAXE))
                .category(CATEGORY_GETTING_STARTED)
                .objective(new CollectItemObjective(Items.COBBLESTONE, 32))
                .objective(new CraftItemObjective(Items.TORCH, 4))
                .reward(new ItemReward(new ItemStack(Items.STONE_PICKAXE)))
                .reward(new ExperienceReward(20))
                .requires(new QuestCompletedCondition(gettingWood))
                .sortOrder(1)
                .build());

        // Getting an Upgrade: prerequisite chain step 2.
        registry.registerQuest(Quest.builder(gettingAnUpgrade)
                .title(Component.literal("Getting an Upgrade"))
                .description(Component.literal("Smelt some iron and craft a proper pickaxe."))
                .icon(new ItemStack(Items.IRON_PICKAXE))
                .category(CATEGORY_GETTING_STARTED)
                .objective(new CraftItemObjective(Items.IRON_PICKAXE, 1))
                .reward(new ItemReward(new ItemStack(Items.IRON_INGOT, 4)))
                .reward(new ExperienceReward(30))
                .requires(new QuestCompletedCondition(stoneAge))
                .sortOrder(2)
                .build());

        // Shiny!: independent of the getting-started tree, tests a second category's own progression.
        registry.registerQuest(Quest.builder(shiny)
                .title(Component.literal("Shiny!"))
                .description(Component.literal("Find a single diamond."))
                .icon(new ItemStack(Items.DIAMOND))
                .category(CATEGORY_DIAMONDS)
                .objective(new CollectItemObjective(Items.DIAMOND, 1))
                .reward(new ExperienceReward(50))
                .autoActivate(true)
                .sortOrder(0)
                .build());

        // Diamond Gear: locked until Shiny! is claimed, demonstrates multiple stacked rewards.
        registry.registerQuest(Quest.builder(diamondGear)
                .title(Component.literal("Diamond Gear"))
                .description(Component.literal("Turn that diamond into a proper tool."))
                .icon(new ItemStack(Items.DIAMOND_PICKAXE))
                .category(CATEGORY_DIAMONDS)
                .objective(new CraftItemObjective(Items.DIAMOND_PICKAXE, 1))
                .reward(new ItemReward(new ItemStack(Items.GOLDEN_APPLE, 1)))
                .reward(new ItemReward(new ItemStack(Items.DIAMOND, 2)))
                .reward(new ExperienceReward(75))
                .requires(new QuestCompletedCondition(shiny))
                .sortOrder(1)
                .build());

        registry.registerQuest(Quest.builder(monsterHunter)
                .title(Component.literal("Monster Hunter"))
                .description(Component.literal("Thin out the local zombie population. Kill 5 zombies."))
                .icon(new ItemStack(Items.ROTTEN_FLESH))
                .category(CATEGORY_COMBAT)
                .objective(new KillEntityObjective(EntityTypes.ZOMBIE, 5))
                .reward(new ItemReward(new ItemStack(Items.IRON_SWORD)))
                .reward(new ExperienceReward(40))
                .autoActivate(true)
                .sortOrder(0)
                .build());

        registry.registerQuest(Quest.builder(creeperAwwMan)
                .title(Component.literal("Creeper? Aww Man"))
                .description(Component.literal("Defeat a creeper without losing your dignity (or your base)."))
                .icon(new ItemStack(Items.GUNPOWDER))
                .category(CATEGORY_COMBAT)
                .objective(new KillEntityObjective(EntityTypes.CREEPER, 1))
                .reward(new ItemReward(new ItemStack(Items.GUNPOWDER, 4)))
                .reward(new ExperienceReward(25))
                .autoActivate(true)
                .sortOrder(1)
                .build());

        registry.registerQuest(Quest.builder(intoTheNether)
                .title(Component.literal("Into the Nether"))
                .description(Component.literal("Build a portal and step through into the Nether."))
                .icon(new ItemStack(Items.NETHERRACK))
                .category(CATEGORY_EXPLORATION)
                .objective(new VisitDimensionObjective(Level.NETHER.identifier()))
                .reward(new ItemReward(new ItemStack(Items.FLINT_AND_STEEL)))
                .reward(new ExperienceReward(60))
                .autoActivate(true)
                .sortOrder(0)
                .build());

        // Gone Fishing: repeatable, deferring to the server's configured default ResetMode.
        registry.registerQuest(Quest.builder(goneFishing)
                .title(Component.literal("Gone Fishing"))
                .description(Component.literal("Reel in 3 fish. Resets on the server's default schedule."))
                .icon(new ItemStack(Items.FISHING_ROD))
                .category(CATEGORY_FARM_AND_SEA)
                .objective(new FishObjective(3))
                .reward(new ExperienceReward(20))
                .autoActivate(true)
                .sortOrder(0)
                .repeatable(24)
                .build());

        // Animal Husbandry: repeatable with an explicit per-quest ResetMode override.
        registry.registerQuest(Quest.builder(animalHusbandry)
                .title(Component.literal("Animal Husbandry"))
                .description(Component.literal("Breed 2 animals. Resets after 1 in-game day."))
                .icon(new ItemStack(Items.WHEAT))
                .category(CATEGORY_FARM_AND_SEA)
                .objective(new BreedAnimalsObjective(2))
                .reward(new ItemReward(new ItemStack(Items.WHEAT, 16)))
                .reward(new ExperienceReward(15))
                .autoActivate(true)
                .sortOrder(1)
                .repeatable(ResetMode.IN_GAME_DAY, 1)
                .build());

        // Thunderstruck: only becomes available during a thunderstorm - demonstrates WeatherCondition
        // and EffectReward. The weather condition fires a trigger when the weather changes (see
        // QuestManagerImpl#tickObjectives), so this unlocks the moment a storm actually starts
        // rather than only at the next login.
        registry.registerQuest(Quest.builder(thunderstruck)
                .title(Component.literal("Thunderstruck"))
                .description(Component.literal("Collect gunpowder while the storm rages."))
                .icon(new ItemStack(Items.GUNPOWDER))
                .category(CATEGORY_WORLD_EVENTS)
                .objective(new CollectItemObjective(Items.GUNPOWDER, 1))
                .reward(new EffectReward(MobEffects.SPEED, 600, 0))
                .requires(new WeatherCondition(WeatherCondition.WeatherType.THUNDER))
                .autoActivate(true)
                .sortOrder(0)
                .build());

        // Night Owl: unlocks at night OR in a dark forest, whichever comes first - demonstrates
        // AnyOfCondition's OR logic (a quest chain otherwise only ever needs ALL of its conditions)
        // and AdvancementReward.
        registry.registerQuest(Quest.builder(nightOwl)
                .title(Component.literal("Night Owl"))
                .description(Component.literal("Hunt zombies after dark, or in a dark forest at any hour. Kill 3 zombies."))
                .icon(new ItemStack(Items.ROTTEN_FLESH))
                .category(CATEGORY_WORLD_EVENTS)
                .objective(new KillEntityObjective(EntityTypes.ZOMBIE, 3))
                .reward(new AdvancementReward(Identifier.withDefaultNamespace("adventure/kill_a_mob")))
                .reward(new ExperienceReward(35))
                .requires(new AnyOfCondition(List.of(
                        new TimeOfDayCondition(false),
                        new BiomeCondition(Biomes.DARK_FOREST))))
                .autoActivate(true)
                .sortOrder(1)
                .build());
    }
}
