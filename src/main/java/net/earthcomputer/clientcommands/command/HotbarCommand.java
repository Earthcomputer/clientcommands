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

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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
        Minecraft client = source.getClient();

        HotbarManager storage = client.getHotbarManager();
        Hotbar entry = storage.get(index - 1);

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            entry.set(slot, source.getPlayer().getInventory().getItem(slot).copy());
        }
        storage.save();

        Component loadKey = client.options.keyLoadHotbarActivator.getTranslatedKeyMessage();
        Component hotbarKey = client.options.keyHotbarSlots[index - 1].getTranslatedKeyMessage();

        source.sendFeedback(Component.translatable("inventory.hotbarSaved", loadKey, hotbarKey));
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        Minecraft client = source.getClient();

        LocalPlayer player = source.getPlayer();
        if (!player.getAbilities().instabuild) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        HotbarManager storage = client.getHotbarManager();
        Hotbar entry = storage.get(index - 1);

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = entry.get(slot).copy();

            player.getInventory().setItem(slot, stack);
            client.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        }

        player.inventoryMenu.broadcastChanges();

        source.sendFeedback(Component.translatable("commands.chotbar.restoredHotbar", index));
        return Command.SINGLE_SUCCESS;
    }
}
