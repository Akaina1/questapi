package com.ryankshah.questapi.impl;

import com.ryankshah.questapi.api.QuestRegistry;
import com.ryankshah.questapi.api.quest.condition.impl.AdvancementCondition;
import com.ryankshah.questapi.api.quest.condition.impl.AnyOfCondition;
import com.ryankshah.questapi.api.quest.condition.impl.BiomeCondition;
import com.ryankshah.questapi.api.quest.condition.impl.ExperienceLevelCondition;
import com.ryankshah.questapi.api.quest.condition.impl.ItemPossessionCondition;
import com.ryankshah.questapi.api.quest.condition.impl.QuestCompletedCondition;
import com.ryankshah.questapi.api.quest.condition.impl.QuestFailedCondition;
import com.ryankshah.questapi.api.quest.condition.impl.TimeOfDayCondition;
import com.ryankshah.questapi.api.quest.condition.impl.WeatherCondition;
import com.ryankshah.questapi.api.quest.objective.impl.BreedAnimalsObjective;
import com.ryankshah.questapi.api.quest.objective.impl.CollectItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.ConsumeItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.CraftItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.FishObjective;
import com.ryankshah.questapi.api.quest.objective.impl.DeliverItemObjective;
import com.ryankshah.questapi.api.quest.objective.impl.KillEntityObjective;
import com.ryankshah.questapi.api.quest.objective.impl.MineBlockObjective;
import com.ryankshah.questapi.api.quest.objective.impl.PlaceBlockObjective;
import com.ryankshah.questapi.api.quest.objective.impl.TameEntityObjective;
import com.ryankshah.questapi.api.quest.objective.impl.VisitDimensionObjective;
import com.ryankshah.questapi.api.quest.objective.impl.VisitLocationObjective;
import com.ryankshah.questapi.api.quest.reward.impl.AdvancementReward;
import com.ryankshah.questapi.api.quest.reward.impl.CommandReward;
import com.ryankshah.questapi.api.quest.reward.impl.EffectReward;
import com.ryankshah.questapi.api.quest.reward.impl.ExperienceReward;
import com.ryankshah.questapi.api.quest.reward.impl.ItemReward;

/**
 * Registers every objective/reward/condition type that ships with the core API itself. These are
 * always registered regardless of dev mode - they are part of the library, not example content.
 */
public final class BuiltinContent {

    private BuiltinContent() {
    }

    public static void registerAll(QuestRegistry registry) {
        registry.registerObjectiveType(CollectItemObjective.TYPE);
        registry.registerObjectiveType(ConsumeItemObjective.TYPE);
        registry.registerObjectiveType(DeliverItemObjective.TYPE);
        registry.registerObjectiveType(MineBlockObjective.TYPE);
        registry.registerObjectiveType(PlaceBlockObjective.TYPE);
        registry.registerObjectiveType(CraftItemObjective.TYPE);
        registry.registerObjectiveType(KillEntityObjective.TYPE);
        registry.registerObjectiveType(TameEntityObjective.TYPE);
        registry.registerObjectiveType(VisitDimensionObjective.TYPE);
        registry.registerObjectiveType(VisitLocationObjective.TYPE);
        registry.registerObjectiveType(FishObjective.TYPE);
        registry.registerObjectiveType(BreedAnimalsObjective.TYPE);

        registry.registerRewardType(ItemReward.TYPE);
        registry.registerRewardType(ExperienceReward.TYPE);
        registry.registerRewardType(CommandReward.TYPE);
        registry.registerRewardType(AdvancementReward.TYPE);
        registry.registerRewardType(EffectReward.TYPE);

        registry.registerConditionType(QuestCompletedCondition.TYPE);
        registry.registerConditionType(QuestFailedCondition.TYPE);
        registry.registerConditionType(AdvancementCondition.TYPE);
        registry.registerConditionType(ItemPossessionCondition.TYPE);
        registry.registerConditionType(ExperienceLevelCondition.TYPE);
        registry.registerConditionType(TimeOfDayCondition.TYPE);
        registry.registerConditionType(WeatherCondition.TYPE);
        registry.registerConditionType(BiomeCondition.TYPE);
        // AnyOfCondition wraps other conditions, so its codec needs the registry itself - see the
        // class Javadoc for why this one can't be a plain static TYPE constant like the others.
        registry.registerConditionType(AnyOfCondition.type(registry));
    }
}
