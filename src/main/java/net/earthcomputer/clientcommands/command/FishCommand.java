package net.earthcomputer.clientcommands.command;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class FishCommand {
    private static final Set<Item> ENCHANTABLE_ITEMS = ImmutableSet.of(Items.BOOK, Items.FISHING_ROD, Items.BOW);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfish")
            .then(literal("list-goals")
                .executes(ctx -> listGoals()))
            .then(literal("add-goal")
                .then(argument("goal", clientItemPredicate())
                    .executes(ctx -> addGoal(getClientItemPredicate(ctx, "goal")))))
            .then(literal("add-enchanted-goal")
                .then(argument("goal", withString(itemAndEnchantmentsPredicate().withItemPredicate(ENCHANTABLE_ITEMS::contains)))
                    .executes(ctx -> addEnchantedGoal(getWithString(ctx, "goal", ItemAndEnchantmentsPredicate.class)))))
            .then(literal("remove-goal")
                .then(argument("index", integer(1))
                    .executes(ctx -> removeGoal(getInteger(ctx, "index"))))));
    }

    private static int listGoals() {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        if (FishingCracker.goals.isEmpty()) {
            sendFeedback(new TranslatableText("commands.cfish.listGoals.noGoals").styled(style -> style.withColor(Formatting.RED)));
        } else {
            sendFeedback("commands.cfish.listGoals.success", FishingCracker.goals.size());
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

        sendFeedback("commands.cfish.addGoal.success", goal.getPrettyString());

        return FishingCracker.goals.size();
    }

    private static int addEnchantedGoal(Pair<String, ItemAndEnchantmentsPredicate> stringAndItemAndEnchantments) {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        String string = stringAndItemAndEnchantments.getLeft();
        ItemAndEnchantmentsPredicate itemAndEnchantments = stringAndItemAndEnchantments.getRight();

        ClientItemPredicate goal = new EnchantedItemPredicate(string, itemAndEnchantments);

        FishingCracker.goals.add(goal);

        sendFeedback("commands.cfish.addGoal.success", string);

        return FishingCracker.goals.size();
    }

    private static int removeGoal(int index) throws CommandSyntaxException {
        if (!checkFishingManipulationEnabled()) {
            return 0;
        }

        if (index > FishingCracker.goals.size()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(index, FishingCracker.goals.size());
        }
        ClientItemPredicate goal = FishingCracker.goals.remove(index - 1);

        sendFeedback("commands.cfish.removeGoal.success", goal.getPrettyString());

        return FishingCracker.goals.size();
    }

    private static boolean checkFishingManipulationEnabled() {
        if (!TempRules.getFishingManipulation().isEnabled()) {
            sendFeedback(new TranslatableText("commands.cfish.needFishingManipulation")
                    .styled(style -> style.withColor(Formatting.RED))
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/ctemprule set fishingManipulation manual")));
            return false;
        } else {
            return true;
        }
    }
}
