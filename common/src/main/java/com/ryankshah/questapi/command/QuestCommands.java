package com.ryankshah.questapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ryankshah.questapi.QuestApi;
import com.ryankshah.questapi.api.quest.Quest;
import com.ryankshah.questapi.api.quest.QuestProgress;
import com.ryankshah.questapi.api.quest.QuestState;
import com.ryankshah.questapi.api.quest.objective.ObjectiveDefinition;
import com.ryankshah.questapi.api.quest.objective.ObjectiveProgress;
import com.ryankshah.questapi.impl.network.QuestNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * Development-only debug commands for exercising the quest lifecycle without playing through it.
 * Only registered when {@code dev=true} - never exposed to production servers.
 */
public final class QuestCommands {

    private QuestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quests")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest", IdentifierArgument.id())
                                        .executes(ctx -> resetQuest(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "quest"))))))
                .then(Commands.literal("fail")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest", IdentifierArgument.id())
                                        .executes(ctx -> failQuest(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "quest"))))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest", IdentifierArgument.id())
                                        .executes(ctx -> unlockQuest(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "quest"))))))
                .then(Commands.literal("progress")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest", IdentifierArgument.id())
                                        .then(Commands.argument("objective", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                        .executes(ctx -> grantProgress(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                IdentifierArgument.getId(ctx, "quest"),
                                                                IntegerArgumentType.getInteger(ctx, "objective"),
                                                                IntegerArgumentType.getInteger(ctx, "amount"))))))))
        );
    }

    private static int resetQuest(CommandSourceStack source, ServerPlayer player, Identifier questId) {
        QuestApi.manager().resetQuest(player, questId);
        QuestNetworking.sendProgress(player);
        source.sendSuccess(() -> Component.literal("Reset quest " + questId + " for " + player.getName().getString()), true);
        return 1;
    }

    private static int failQuest(CommandSourceStack source, ServerPlayer player, Identifier questId) {
        if (!QuestApi.manager().failQuest(player, questId)) {
            source.sendFailure(Component.literal("Quest " + questId + " is not active for " + player.getName().getString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Failed quest " + questId + " for " + player.getName().getString()), true);
        return 1;
    }

    private static int unlockQuest(CommandSourceStack source, ServerPlayer player, Identifier questId) {
        if (QuestApi.registry().getQuest(questId).isEmpty()) {
            source.sendFailure(Component.literal("Unknown quest " + questId));
            return 0;
        }
        QuestApi.manager().refreshAvailability(player);
        QuestNetworking.sendProgress(player);
        QuestState state = QuestApi.manager().getState(player, questId);
        source.sendSuccess(() -> Component.literal("Re-evaluated availability for " + player.getName().getString()
                + "; quest " + questId + " is " + state), true);
        return 1;
    }

    private static int grantProgress(CommandSourceStack source, ServerPlayer player, Identifier questId, int objectiveIndex, int amount) {
        Quest quest = QuestApi.registry().getQuest(questId).orElse(null);
        if (quest == null) {
            source.sendFailure(Component.literal("Unknown quest " + questId));
            return 0;
        }
        List<ObjectiveDefinition> objectives = quest.objectives();
        if (objectiveIndex < 0 || objectiveIndex >= objectives.size()) {
            source.sendFailure(Component.literal("Quest " + questId + " has no objective " + objectiveIndex));
            return 0;
        }
        QuestProgress progress = QuestApi.manager().getProgress(player, questId);
        if (progress.state() != QuestState.ACTIVE) {
            source.sendFailure(Component.literal("Quest " + questId + " is not active for " + player.getName().getString()));
            return 0;
        }
        ObjectiveDefinition definition = objectives.get(objectiveIndex);
        ObjectiveProgress op = progress.objective(objectiveIndex);
        op.updateCurrent(op.current() + amount, definition.targetAmount());
        if (progress.objectivesMet(quest)) {
            progress.setState(QuestState.COMPLETED);
            progress.setCompletedAt(System.currentTimeMillis());
        }
        QuestNetworking.sendProgress(player);
        source.sendSuccess(() -> Component.literal("Granted " + amount + " progress on objective " + objectiveIndex + " of " + questId), true);
        return 1;
    }
}
