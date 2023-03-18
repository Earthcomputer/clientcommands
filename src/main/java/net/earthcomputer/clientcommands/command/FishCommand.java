package net.earthcomputer.clientcommands.command;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FishCommand {

    private static final Set<Item> ENCHANTABLE_ITEMS = ImmutableSet.of(Items.BOOK, Items.FISHING_ROD, Items.BOW);

    private static final SimpleCommandExceptionType NEED_FISHING_MANIPULATION_EXCEPTION = new SimpleCommandExceptionType(
        Text.translatable("commands.cfish.needFishingManipulation")
            .styled(style -> style.withColor(Formatting.RED))
            .append(" ")
            .append(getCommandTextComponent("commands.client.enable", "/ctemprule set fishingManipulation manual")));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cfish")
            .then(literal("list-goals")
                .executes(ctx -> listGoals(ctx.getSource())))
            .then(literal("add-goal")
                .then(argument("goal", clientItemPredicate(registryAccess))
                    .executes(ctx -> addGoal(ctx.getSource(), getClientItemPredicate(ctx, "goal")))))
            .then(literal("add-enchanted-goal")
                .then(argument("goal", withString(itemAndEnchantmentsPredicate().withItemPredicate(ENCHANTABLE_ITEMS::contains)))
                    .executes(ctx -> addEnchantedGoal(ctx.getSource(), getWithString(ctx, "goal", ItemAndEnchantmentsPredicate.class)))))
            .then(literal("remove-goal")
                .then(argument("index", integer(1))
                    .executes(ctx -> removeGoal(ctx.getSource(), getInteger(ctx, "index"))))));
    }

    private static int listGoals(FabricClientCommandSource source) throws CommandSyntaxException {
        if (!TempRules.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        if (FishingCracker.goals.isEmpty()) {
            source.sendFeedback(Text.translatable("commands.cfish.listGoals.noGoals").styled(style -> style.withColor(Formatting.RED)));
        } else {
            source.sendFeedback(Text.translatable("commands.cfish.listGoals.success", FishingCracker.goals.size()));
            for (int i = 0; i < FishingCracker.goals.size(); i++) {
                source.sendFeedback(Text.of((i + 1) + ": " + FishingCracker.goals.get(i).getPrettyString()));
            }
        }

        return FishingCracker.goals.size();
    }

    private static int addGoal(FabricClientCommandSource source, ClientItemPredicateArgumentType.ClientItemPredicate goal) throws CommandSyntaxException {
        if (!TempRules.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        FishingCracker.goals.add(goal);

        source.sendFeedback(Text.translatable("commands.cfish.addGoal.success", goal.getPrettyString()));

        return FishingCracker.goals.size();
    }

    private static int addEnchantedGoal(FabricClientCommandSource source, Pair<String, ItemAndEnchantmentsPredicate> stringAndItemAndEnchantments) throws CommandSyntaxException {
        if (!TempRules.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        String string = stringAndItemAndEnchantments.getLeft();
        ItemAndEnchantmentsPredicate itemAndEnchantments = stringAndItemAndEnchantments.getRight();

        ClientItemPredicate goal = new EnchantedItemPredicate(string, itemAndEnchantments);

        FishingCracker.goals.add(goal);

        source.sendFeedback(Text.translatable("commands.cfish.addGoal.success", string));

        return FishingCracker.goals.size();
    }

    private static int removeGoal(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        if (!TempRules.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        if (index > FishingCracker.goals.size()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(index, FishingCracker.goals.size());
        }
        ClientItemPredicate goal = FishingCracker.goals.remove(index - 1);

        source.sendFeedback(Text.translatable("commands.cfish.removeGoal.success", goal.getPrettyString()));

        return FishingCracker.goals.size();
    }
}
