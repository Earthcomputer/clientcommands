package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CGiveCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cgive.notCreative"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cgive")
            .then(argument("item", itemStack(registryAccess))
            .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), 1))
                .then(argument("count", integer(1))
                .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), getInteger(ctx, "count"))))));
    }

    private static int give(FabricClientCommandSource source, ItemStackArgument itemArgument, int count) throws CommandSyntaxException {
        if (!source.getPlayer().getAbilities().creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ItemStack stack = itemArgument.createStack(Math.min(count, itemArgument.getItem().getMaxCount()), false);
        source.getClient().interactionManager.clickCreativeStack(stack, 36 + source.getPlayer().getInventory().selectedSlot);
        source.getPlayer().playerScreenHandler.sendContentUpdates();

        source.sendFeedback(Text.translatable("commands.cgive.success", count, stack.toHoverableText()));
        return Command.SINGLE_SUCCESS;
    }
}
