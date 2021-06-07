package net.earthcomputer.clientcommands.command;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.HotbarStorage;
import net.minecraft.client.option.HotbarStorageEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class HotbarCommand {

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.chotbar.notCreative"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("chotbar");

        dispatcher.register(literal("chotbar")
            .then(literal("save")
                // Intentionally one-indexed to match keybindings and creative inventory display
                .then(argument("index", integer(1, 9))
                .executes(ctx -> save(ctx.getSource(), getInteger(ctx, "index")))))
            .then(literal("restore")
                .then(argument("index", integer(1, 9))
                .executes(ctx -> restore(ctx.getSource(), getInteger(ctx, "index"))))));
    }

    private static int save(ServerCommandSource source, int index) throws CommandSyntaxException {
        MinecraftClient client = MinecraftClient.getInstance();

        HotbarStorage storage = client.getCreativeHotbarStorage();
        HotbarStorageEntry entry = storage.getSavedHotbar(index - 1);

        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            entry.set(slot, client.player.getInventory().getStack(slot).copy());
        }
        storage.save();

        Text loadKey = client.options.keyLoadToolbarActivator.getBoundKeyLocalizedText();
        Text hotbarKey = client.options.keysHotbar[index - 1].getBoundKeyLocalizedText();

        sendFeedback(new TranslatableText("inventory.hotbarSaved", loadKey, hotbarKey));
        return 0;
    }

    private static int restore(ServerCommandSource source, int index) throws CommandSyntaxException {
        MinecraftClient client = MinecraftClient.getInstance();

        ClientPlayerEntity player = client.player;
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

        sendFeedback(new TranslatableText("commands.chotbar.restoredHotbar", index));
        return 0;
    }

}
