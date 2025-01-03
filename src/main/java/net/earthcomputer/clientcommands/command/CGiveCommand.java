package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CItemArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CGiveCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cgive.notCreative"));
    private static final SimpleCommandExceptionType NO_SPACE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cgive.noSpace"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cgive")
            .then(argument("item", itemStack(context))
            .executes(ctx -> give(ctx.getSource(), getItemStackArgument(ctx, "item"), 1))
                .then(argument("count", integer(1))
                .executes(ctx -> give(ctx.getSource(), getItemStackArgument(ctx, "item"), getInteger(ctx, "count"))))));
    }

    private static int give(FabricClientCommandSource source, ItemInput itemInput, int count) throws CommandSyntaxException {
        LocalPlayer player = source.getPlayer();
        if (!player.isCreative()) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }
        MultiPlayerGameMode interactionManager = source.getClient().gameMode;
        assert interactionManager != null;

        ItemStack stack = itemInput.createItemStack(count, false);

        IntList changedSlots = new IntArrayList();
        int simulatedCount = stack.getCount();
        int prevCount;
        do {
            prevCount = simulatedCount;
            int slot = getSlotWithRemainingSpace(player.getInventory(), stack, changedSlots);
            if (slot == -1) {
                slot = player.getInventory().getFreeSlot();
            }
            if (slot != -1) {
                ItemStack stackInSlot = player.getInventory().getItem(slot);
                simulatedCount = Math.clamp(simulatedCount - (player.getInventory().getMaxStackSize(stackInSlot) - stackInSlot.getCount()), 0, simulatedCount);
                changedSlots.add(slot);
            }
        } while (simulatedCount != 0 && simulatedCount != prevCount);

        if (simulatedCount != 0) {
            throw NO_SPACE_EXCEPTION.create();
        }

        for (int i = 0; i < changedSlots.size(); i++) {
            int slot = changedSlots.getInt(i);
            stack.setCount(player.getInventory().addResource(slot, stack));
            interactionManager.handleCreativeModeItemAdd(player.getInventory().getItem(slot), inventoryToSlotId(slot));
        }

        player.inventoryMenu.broadcastChanges();

        // recreate the stack, otherwise it shows that the player was given air
        stack = itemInput.createItemStack(count, false);
        source.sendFeedback(Component.translatable("commands.cgive.success", count, stack.getDisplayName()));
        return Command.SINGLE_SUCCESS;
    }

    private static int getSlotWithRemainingSpace(Inventory inventory, ItemStack stack, IntList fullSlots) {
        if (inventory.hasRemainingSpaceForItem(inventory.getItem(inventory.selected), stack) && !fullSlots.contains(inventory.selected)) {
            return inventory.selected;
        } else if (inventory.hasRemainingSpaceForItem(inventory.getItem(Inventory.SLOT_OFFHAND), stack) && !fullSlots.contains(Inventory.SLOT_OFFHAND)) {
            return inventory.selected;
        } else {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (inventory.hasRemainingSpaceForItem(inventory.getItem(slot), stack) && !fullSlots.contains(slot)) {
                    return slot;
                }
            }
            return -1;
        }
    }

    private static int inventoryToSlotId(int inventoryId) {
        return inventoryId < Inventory.SELECTION_SIZE
            ? InventoryMenu.USE_ROW_SLOT_START + inventoryId
            : InventoryMenu.INV_SLOT_START - Inventory.SELECTION_SIZE + inventoryId;
    }
}
