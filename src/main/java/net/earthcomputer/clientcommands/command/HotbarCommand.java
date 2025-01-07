package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class HotbarCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.chotbar.notCreative"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("chotbar")
            .then(literal("save")
                // Intentionally one-indexed to match keybindings and creative inventory display
                .then(argument("index", integer(1, 9))
                .executes(ctx -> save(ctx.getSource(), getInteger(ctx, "index")))))
            .then(literal("restore")
                .then(argument("index", integer(1, 9))
                .executes(ctx -> restore(ctx.getSource(), getInteger(ctx, "index"))))));
    }

    private static int save(FabricClientCommandSource source, int index) {
        Minecraft minecraft = source.getClient();

        HotbarManager manager = minecraft.getHotbarManager();
        Hotbar hotbar = manager.get(index - 1);

        hotbar.storeFrom(source.getPlayer().getInventory(), source.registryAccess());
        manager.save();

        Component loadKey = minecraft.options.keyLoadHotbarActivator.getTranslatedKeyMessage();
        Component hotbarKey = minecraft.options.keyHotbarSlots[index - 1].getTranslatedKeyMessage();

        source.sendFeedback(Component.translatable("inventory.hotbarSaved", loadKey, hotbarKey));
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        Minecraft minecraft = source.getClient();

        LocalPlayer player = source.getPlayer();
        if (!player.getAbilities().instabuild) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        HotbarManager manager = minecraft.getHotbarManager();
        Hotbar hotbar = manager.get(index - 1);
        List<ItemStack> hotbarItems = hotbar.load(source.registryAccess());

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = hotbarItems.get(slot);

            player.getInventory().setItem(slot, stack);
            minecraft.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        }

        player.inventoryMenu.broadcastChanges();

        source.sendFeedback(Component.translatable("commands.chotbar.restoredHotbar", index));
        return Command.SINGLE_SUCCESS;
    }
}
