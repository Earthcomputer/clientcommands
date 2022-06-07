package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;

import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CalcStackCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccalcstack")
            .then(argument("count", integer(0))
                .then(argument("item", itemStack())
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
                source.sendFeedback(new TranslatableText("commands.ccalcstack.success.empty.exact", count, stacks));
            } else {
                source.sendFeedback(new TranslatableText("commands.ccalcstack.success.empty", count, stacks, remainder));
            }
        } else {
            Text itemText = stack.toHoverableText();
            if (remainder == 0) {
                source.sendFeedback(new TranslatableText("commands.ccalcstack.success.exact", count, itemText, stacks));
            } else {
                source.sendFeedback(new TranslatableText("commands.ccalcstack.success", count, itemText, stacks, remainder));
            }
        }

        return 1;
    }

    private static int getStackSize(FabricClientCommandSource source, int count) {
        ItemStack heldStack = source.getPlayer().getStackInHand(Hand.MAIN_HAND).copy();
        return getStackSize(source, heldStack, count);
    }

}
