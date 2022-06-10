package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CalcStackCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("ccalcstack")
            .then(argument("count", integer(0))
                .then(argument("item", itemStack(registryAccess))
                .executes(ctx -> {
                    ItemStack stack = getCItemStackArgument(ctx, "item").createStack(1, false);
                    return getStackSize(ctx.getSource(), stack, getInteger(ctx, "count"));
                }))
            .executes(ctx -> getStackSize(ctx.getSource(), getInteger(ctx, "count")))));
    }

    private static int getStackSize(FabricClientCommandSource source, ItemStack stack, int count) {
        int stacks = count / stack.getMaxCount();
        int remainder = count % stack.getMaxCount();

        if (stack.isEmpty()) {
            if (remainder == 0) {
                source.sendFeedback(Text.translatable("commands.ccalcstack.success.empty.exact", count, stacks));
            } else {
                source.sendFeedback(Text.translatable("commands.ccalcstack.success.empty", count, stacks, remainder));
            }
        } else {
            Text itemText = stack.toHoverableText();
            if (remainder == 0) {
                source.sendFeedback(Text.translatable("commands.ccalcstack.success.exact", count, itemText, stacks));
            } else {
                source.sendFeedback(Text.translatable("commands.ccalcstack.success", count, itemText, stacks, remainder));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int getStackSize(FabricClientCommandSource source, int count) {
        ItemStack heldStack = source.getPlayer().getStackInHand(Hand.MAIN_HAND).copy();
        return getStackSize(source, heldStack, count);
    }

}
