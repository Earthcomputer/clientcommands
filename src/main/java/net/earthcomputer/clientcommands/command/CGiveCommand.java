package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CGiveCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cgive.notCreative"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cgive")
            .then(argument("item", itemStack())
            .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), 1))
                .then(argument("count", integer(1))
                .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), getInteger(ctx, "count"))))));
    }

    private static int give(FabricClientCommandSource source, ItemStackArgument itemArgument, int count) throws CommandSyntaxException {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (!client.player.getAbilities().creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ItemStack stack = itemArgument.createStack(Math.min(count, itemArgument.getItem().getMaxCount()), false);
        client.interactionManager.clickCreativeStack(stack, 36 + client.player.getInventory().selectedSlot);
        client.player.playerScreenHandler.sendContentUpdates();

        sendFeedback(new TranslatableText("commands.cgive.success", count, stack.toHoverableText()));
        return 0;
    }
}
