package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.CCrackVillager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;

public class CrackVillagerRNGCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccrackvillager")
                .then(literal("cancel")
                        .executes(ctx -> cancel(ctx.getSource())))
                .then(literal("clock")
                        .then(argument("clockpos", blockPos())
                                .executes(ctx -> crackVillagerRNG(ctx.getSource(), getBlockPos(ctx, "clockpos")))
                                .then(literal("interval")
                                        .then(argument("ticks", integer(20, 100))
                                                .executes(ctx -> crackWithInterval(ctx.getSource(), getBlockPos(ctx, "clockpos"), getInteger(ctx, "ticks"))))))
                                )
                .then(literal("enchant").then(argument("name", itemAndEnchantmentsPredicate().withItemPredicate((i) -> i.equals(Items.BOOK)).constrainMaxLevel())
                        .executes(ctx -> lookingForEnchantment(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "name"))))));
    }

    private static int lookingForEnchantment(FabricClientCommandSource source, ItemAndEnchantmentsPredicate predicate) {
        CCrackVillager.targetEnchantment = predicate;
        return Command.SINGLE_SUCCESS;
    }

    private static int crackVillagerRNG(FabricClientCommandSource source, BlockPos pos) throws CommandSyntaxException {
        CCrackVillager.clockPos = pos;
        CCrackVillager.crackVillager(source.getPlayer(), seed -> {
            source.sendFeedback(Component.translatable("commands.ccrackvillager.success", Long.toHexString(seed)));
            PlayerRandCracker.setSeed(seed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(FabricClientCommandSource source) {
        CCrackVillager.cancel();
        CCrackVillager.targetEnchantment = null;
        source.sendFeedback(Component.translatable("commands.ccrackvillager.cancel"));
        return Command.SINGLE_SUCCESS;
    }

    private static int crackWithInterval(FabricClientCommandSource source, BlockPos pos, int interval) throws CommandSyntaxException {
        CCrackVillager.setInterval(interval);
        return crackVillagerRNG(source, pos);
    }
}
