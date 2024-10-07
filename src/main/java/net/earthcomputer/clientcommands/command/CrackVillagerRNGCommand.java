package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.features.CCrackVillager;
import net.earthcomputer.clientcommands.features.VillagerRNGSim;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.earthcomputer.clientcommands.command.arguments.DynamicIntegerArgument.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.CombinedArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.EnchantmentArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.DynamicIntegerArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;

import static net.earthcomputer.clientcommands.command.arguments.CachedItemArgument.item;

public class CrackVillagerRNGCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("ccrackvillager")
            .then(literal("cancel")
                .executes(ctx -> cancel(ctx.getSource())))
            .then(literal("clock")
                .then(argument("clockpos", blockPos())
                    .executes(ctx -> crackVillagerRNG(ctx.getSource(), getBlockPos(ctx, "clockpos")))))
            .then(literal("interval")
                .then(argument("ticks", IntegerArgumentType.integer(0, 20))
                    .executes(ctx -> setInterval(ctx.getSource(), getInteger(ctx, "ticks")))))
            .then(literal("add-goal")
                .then(genFirst(context))
                .then(genSecond(context))
                .then(genResult(context)))
            .then(literal("list-goals")
                .executes(CrackVillagerRNGCommand::listGoals))
            .then(literal("remove-goal")
                .then(argument("index", integer(1,CCrackVillager.goalOffers::size))
                    .executes(CrackVillagerRNGCommand::removeGoal)))
            .then(literal("clear-goals").executes(CrackVillagerRNGCommand::clearGoals))
            .then(literal("run").executes(CrackVillagerRNGCommand::doRun)));
    }

    private static int doRun(CommandContext<FabricClientCommandSource> context) {
        CCrackVillager.findingOffers = true;
        if(CCrackVillager.goalOffers.isEmpty()) {
            context.getSource().sendFeedback(Component.translatable("commands.ccrackvillager.emptyGoals"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int clearGoals(CommandContext<FabricClientCommandSource> context) {
        CCrackVillager.goalOffers.clear();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeGoal(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.translatable("commands.ccrackvillager.removeGoal", CCrackVillager.goalOffers.remove(getInteger(context, "index") - 1)));
        return Command.SINGLE_SUCCESS;
    }

    private static int listGoals(CommandContext<FabricClientCommandSource> context) {
        for(var i = 0; i < CCrackVillager.goalOffers.size(); i++) {
            var offer = CCrackVillager.goalOffers.get(i);
            context.getSource().sendFeedback(Component.translatable("commands.ccrackvillager.listGoal", i + 1, offer));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int crackVillagerRNG(FabricClientCommandSource source, BlockPos pos) throws CommandSyntaxException {
        CCrackVillager.clockPos = pos;
        VillagerRNGSim.commandSource = source;
        CCrackVillager.crackVillager(source.getPlayer(), seed -> {
            source.sendFeedback(Component.translatable("commands.ccrackvillager.success", Long.toHexString(seed)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(FabricClientCommandSource source) {
        CCrackVillager.cancel();
        CCrackVillager.findingOffers = false;
        source.sendFeedback(Component.translatable("commands.ccrackvillager.cancel"));
        return Command.SINGLE_SUCCESS;
    }

    private static int setInterval(FabricClientCommandSource source, int interval) throws CommandSyntaxException {
        CCrackVillager.setInterval(interval);
        return Command.SINGLE_SUCCESS;
    }

    private static Combined<Predicate<ItemStack>, String> createPredicate(CommandContext<FabricClientCommandSource> context, String argName) {
        try {
            Result<Combined<ItemInput, Combined<Integer, Integer>>> result = getWithString(context, argName, null);
            var combined = result.value();
            var item = combined.first().getItem();
            var min = combined.second().first();
            var max = combined.second().second();
            return new Combined<>((stack) -> stack.is(item)
                && ((min > item.getDefaultMaxStackSize() || min <= stack.getCount())
                && (max > item.getDefaultMaxStackSize() || stack.getCount() <= max)),
                result.string().replaceAll("\\* \\*","*").replaceAll("([\\d*]+) ([\\d*]+)$", "$1-$2"));
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    private static int setGoal(CommandContext<FabricClientCommandSource> context) {
        CCrackVillager.Offer offer = new CCrackVillager.Offer();

        var combined = createPredicate(context, "firstitem");
        if(combined != null) offer.withFirst(combined.first(), combined.second());
        combined = createPredicate(context, "seconditem");
        if(combined != null) offer.withSecond(combined.first(), combined.second());
        combined = createPredicate(context, "resultitem");
        if(combined != null) offer.withResult(combined.first(), combined.second());

        try {
            Result<Combined<Enchantment, Integer>> result2 = getWithString(context, "enchantment", null);
            var enchantment = result2.value().first();
            offer.andEnchantment((stack) -> {
                var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
                var level = enchantments.getLevel(enchantment);
                return (result2.value().second() > enchantment.getMaxLevel() && level > 0) || result2.value().second() == level;
            }, result2.string());
        } catch (IllegalArgumentException ignored) { }

        context.getSource().sendFeedback(Component.literal((CCrackVillager.goalOffers.size()+1) + ": " + offer));

        CCrackVillager.goalOffers.add(offer);

        return Command.SINGLE_SUCCESS;
    }



    static LiteralArgumentBuilder<FabricClientCommandSource> genFirst(CommandBuildContext context) {
        var item = item(context);
        Supplier<Integer> supplier = () -> item.lastItem.getItem().getDefaultMaxStackSize();
        return literal("first")
            .then(argument("firstitem", withString(combined(item, combined(integer(1, supplier).allowAny(), integer(1, supplier).allowAny()))))
                .executes(CrackVillagerRNGCommand::setGoal)
                .then(genSecond(context)) .then(genResult(context)));
    }

    static  LiteralArgumentBuilder<FabricClientCommandSource> genSecond(CommandBuildContext context) {
        var item = item(context);
        Supplier<Integer> supplier = () -> item.lastItem.getItem().getDefaultMaxStackSize();
        return literal("second")
            .then(argument("seconditem", withString(combined(item, combined(integer(1, supplier).allowAny(), integer(1, supplier).allowAny()))))
                .executes(CrackVillagerRNGCommand::setGoal) .then(genResult(context)));
    }

    static LiteralArgumentBuilder<FabricClientCommandSource> genResult(CommandBuildContext context) {
        var enchantment = enchantment();
        var item = item(context);
        Supplier<Integer> supplier = () -> item.lastItem.getItem().getDefaultMaxStackSize();
        return literal("result")
            .then(argument("resultitem", withString(combined(item, combined(integer(1, supplier).allowAny(), integer(1, supplier).allowAny()))))
                .executes(CrackVillagerRNGCommand::setGoal) .then(literal("enchant")
                    .then(argument("enchantment", withString(combined(enchantment, integer(() -> enchantment.lastParsed.getMaxLevel()).allowAny())))
                        .executes(CrackVillagerRNGCommand::setGoal))));
    }
}
