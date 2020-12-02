package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class FishCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfish");

        dispatcher.register(literal("cfish")
            .then(literal("list-goals")
                .executes(ctx -> listGoals()))
            .then(literal("add-goal")
                .then(argument("goal", clientItemPredicate())
                    .executes(ctx -> addGoal(getClientItemPredicate(ctx, "goal")))))
            .then(literal("remove-goal")
                .then(argument("index", integer(1))
                    .executes(ctx -> removeGoal(getInteger(ctx, "index"))))));
    }

    private static int listGoals() {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        if (FishingCracker.goals.isEmpty()) {
            sendFeedback(new TranslatableText("commands.cfish.noGoals").styled(style -> style.withColor(Formatting.RED)));
        } else {
            for (int i = 0; i < FishingCracker.goals.size(); i++) {
                sendFeedback((i + 1) + ": " + FishingCracker.goals.get(i).getPrettyString());
            }
        }

        return FishingCracker.goals.size();
    }

    private static int addGoal(ClientItemPredicateArgumentType.ClientItemPredicate goal) {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        FishingCracker.goals.add(goal);
        return FishingCracker.goals.size();
    }

    private static int removeGoal(int index) throws CommandSyntaxException {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        if (index > FishingCracker.goals.size()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(index, FishingCracker.goals.size());
        }
        FishingCracker.goals.remove(index - 1);
        return FishingCracker.goals.size();
    }

    private static boolean checkFishingManipulationEnabled() {
        if (!TempRules.getFishingManipulation()) {
            sendFeedback(new TranslatableText("commands.cfish.needFishingManipulation")
                    .styled(style -> style.withColor(Formatting.RED))
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/ctemprule set fishingManipulation true")));
            return false;
        } else {
            return true;
        }
    }
}
