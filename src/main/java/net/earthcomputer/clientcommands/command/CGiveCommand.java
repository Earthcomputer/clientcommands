package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.getCItemStackArgument;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.itemStack;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CGiveCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cgive.notCreative"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("cgive")
            .then(argument("item", itemStack(registryAccess))
            .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), 1))
                .then(argument("count", integer(1))
                .executes(ctx -> give(ctx.getSource(), getCItemStackArgument(ctx, "item"), getInteger(ctx, "count"))))));
    }

    private static int give(FabricClientCommandSource source, ItemInput itemArgument, int count) throws CommandSyntaxException {
        if (!source.getPlayer().getAbilities().instabuild) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ItemStack stack = itemArgument.createItemStack(Math.min(count, itemArgument.getItem().getMaxStackSize()), false);
        source.getClient().gameMode.handleCreativeModeItemAdd(stack, 36 + source.getPlayer().getInventory().selected);
        source.getPlayer().inventoryMenu.broadcastChanges();

        source.sendFeedback(Component.translatable("commands.cgive.success", count, stack.getDisplayName()));
        return Command.SINGLE_SUCCESS;
    }
}
