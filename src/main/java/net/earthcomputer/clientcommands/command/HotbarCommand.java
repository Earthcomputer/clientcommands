package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.HotbarStorage;
import net.minecraft.client.option.HotbarStorageEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class HotbarCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.chotbar.notCreative"));

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
        MinecraftClient client = source.getClient();

        HotbarStorage storage = client.getCreativeHotbarStorage();
        HotbarStorageEntry entry = storage.getSavedHotbar(index - 1);

        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            entry.set(slot, source.getPlayer().getInventory().getStack(slot).copy());
        }
        storage.save();

        Text loadKey = client.options.loadToolbarActivatorKey.getBoundKeyLocalizedText();
        Text hotbarKey = client.options.hotbarKeys[index - 1].getBoundKeyLocalizedText();

        source.sendFeedback(Text.translatable("inventory.hotbarSaved", loadKey, hotbarKey));
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        MinecraftClient client = source.getClient();

        ClientPlayerEntity player = source.getPlayer();
        if (!player.getAbilities().creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        HotbarStorage storage = client.getCreativeHotbarStorage();
        HotbarStorageEntry entry = storage.getSavedHotbar(index - 1);

        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = entry.get(slot).copy();

            player.getInventory().setStack(slot, stack);
            client.interactionManager.clickCreativeStack(stack, 36 + slot);
        }

        player.playerScreenHandler.sendContentUpdates();

        source.sendFeedback(Text.translatable("commands.chotbar.restoredHotbar", index));
        return Command.SINGLE_SUCCESS;
    }
}
