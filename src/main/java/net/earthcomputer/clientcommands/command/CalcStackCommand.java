package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.getCItemStackArgument;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.itemStack;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CalcStackCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("ccalcstack")
            .then(argument("count", integer(0))
                .then(argument("item", itemStack(registryAccess))
                .executes(ctx -> {
                    ItemStack stack = getCItemStackArgument(ctx, "item").createItemStack(1, false);
                    return getStackSize(ctx.getSource(), stack, getInteger(ctx, "count"));
                }))
            .executes(ctx -> getStackSize(ctx.getSource(), getInteger(ctx, "count")))));
    }

    private static int getStackSize(FabricClientCommandSource source, ItemStack stack, int count) {
        int stacks = count / stack.getMaxStackSize();
        int remainder = count % stack.getMaxStackSize();

        if (stack.isEmpty()) {
            if (remainder == 0) {
                source.sendFeedback(Component.translatable("commands.ccalcstack.success.empty.exact", count, stacks));
            } else {
                source.sendFeedback(Component.translatable("commands.ccalcstack.success.empty", count, stacks, remainder));
            }
        } else {
            Component itemText = stack.getDisplayName();
            if (remainder == 0) {
                source.sendFeedback(Component.translatable("commands.ccalcstack.success.exact", count, itemText, stacks));
            } else {
                source.sendFeedback(Component.translatable("commands.ccalcstack.success", count, itemText, stacks, remainder));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int getStackSize(FabricClientCommandSource source, int count) {
        ItemStack heldStack = source.getPlayer().getItemInHand(InteractionHand.MAIN_HAND).copy();
        return getStackSize(source, heldStack, count);
    }

}
