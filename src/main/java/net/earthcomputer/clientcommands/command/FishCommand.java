package net.earthcomputer.clientcommands.command;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgument;
import net.earthcomputer.clientcommands.command.arguments.WithStringArgument;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FishCommand {

    private static final Set<Item> ENCHANTABLE_ITEMS = ImmutableSet.of(Items.BOOK, Items.FISHING_ROD, Items.BOW);

    private static final SimpleCommandExceptionType NEED_FISHING_MANIPULATION_EXCEPTION = new SimpleCommandExceptionType(
        Component.translatable("commands.cfish.needFishingManipulation")
            .withStyle(style -> style.withColor(ChatFormatting.RED))
            .append(" ")
            .append(getCommandTextComponent("commands.client.enable", "/cconfig clientcommands fishingManipulation set MANUAL")));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() >= MultiVersionCompat.V1_20) {
            return; // fishing manipulation patched in 1.20
        }

        dispatcher.register(literal("cfish")
            .then(literal("list-goals")
                .executes(ctx -> listGoals(ctx.getSource())))
            .then(literal("add-goal")
                .then(argument("goal", withString(clientItemPredicate(context)))
                    .executes(ctx -> addGoal(ctx.getSource(), getWithString(ctx, "goal", ClientItemPredicateArgument.ClientItemPredicate.class)))))
            .then(literal("add-enchanted-goal")
                .then(argument("goal", withString(itemAndEnchantmentsPredicate().withItemPredicate(ENCHANTABLE_ITEMS::contains).withEnchantmentPredicate((item, ench) -> ench.isDiscoverable()).constrainMaxLevel()))
                    .executes(ctx -> addEnchantedGoal(ctx.getSource(), getWithString(ctx, "goal", ItemAndEnchantmentsPredicate.class)))))
            .then(literal("remove-goal")
                .then(argument("index", integer(1))
                    .executes(ctx -> removeGoal(ctx.getSource(), getInteger(ctx, "index"))))));
    }

    private static int listGoals(FabricClientCommandSource source) throws CommandSyntaxException {
        if (!Configs.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        if (FishingCracker.goals.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cfish.listGoals.noGoals").withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            source.sendFeedback(Component.translatable("commands.cfish.listGoals.success", FishingCracker.goals.size()));
            for (int i = 0; i < FishingCracker.goals.size(); i++) {
                source.sendFeedback(Component.literal((i + 1) + ": " + FishingCracker.goals.get(i).string()));
            }
        }

        return FishingCracker.goals.size();
    }

    private static int addGoal(FabricClientCommandSource source, WithStringArgument.Result<ClientItemPredicateArgument.ClientItemPredicate> goal) throws CommandSyntaxException {
        if (!Configs.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        FishingCracker.goals.add(goal);

        source.sendFeedback(Component.translatable("commands.cfish.addGoal.success", goal.string()));

        return FishingCracker.goals.size();
    }

    private static int addEnchantedGoal(FabricClientCommandSource source, WithStringArgument.Result<ItemAndEnchantmentsPredicate> stringAndItemAndEnchantments) throws CommandSyntaxException {
        if (!Configs.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        String string = stringAndItemAndEnchantments.string();
        ItemAndEnchantmentsPredicate itemAndEnchantments = stringAndItemAndEnchantments.value();

        ClientItemPredicate goal = new EnchantedItemPredicate(itemAndEnchantments);

        FishingCracker.goals.add(new Result<>(string, goal));

        source.sendFeedback(Component.translatable("commands.cfish.addGoal.success", string));

        return FishingCracker.goals.size();
    }

    private static int removeGoal(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        if (!Configs.getFishingManipulation().isEnabled()) {
            throw NEED_FISHING_MANIPULATION_EXCEPTION.create();
        }

        if (index > FishingCracker.goals.size()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(index, FishingCracker.goals.size());
        }
        var goal = FishingCracker.goals.remove(index - 1);

        source.sendFeedback(Component.translatable("commands.cfish.removeGoal.success", goal.string()));

        return FishingCracker.goals.size();
    }
}
