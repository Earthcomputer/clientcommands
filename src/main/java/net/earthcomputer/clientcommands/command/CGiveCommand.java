package net.earthcomputer.clientcommands.command;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.ItemStackArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

public class CGiveCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cgive.notCreative"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cgive");

        dispatcher.register(literal("cgive")
            .then(argument("item", itemStack())
            .executes(ctx -> give(ctx.getSource(), getItemStackArgument(ctx, "item"), 1))
                .then(argument("count", integer(1))
                .executes(ctx -> give(ctx.getSource(), getItemStackArgument(ctx, "item"), getInteger(ctx, "count"))))));
    }

    private static int give(ServerCommandSource source, ItemStackArgument itemArgument, int count) throws CommandSyntaxException {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!player.abilities.creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ItemStack stack = itemArgument.createStack(Math.min(count, itemArgument.getItem().getMaxCount()), false);

        PlayerInventory inventory = player.inventory;
        inventory.setStack(inventory.selectedSlot, stack);
        MinecraftClient.getInstance().interactionManager.clickCreativeStack(stack, 36 + inventory.selectedSlot);
        player.playerScreenHandler.sendContentUpdates();

        sendFeedback(new TranslatableText("commands.cgive.success", count, stack.toHoverableText()));
        return 0;
    }

}
