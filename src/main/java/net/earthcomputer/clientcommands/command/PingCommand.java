package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.TranslatableText;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class PingCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cping")
            .executes(ctx -> printPing(ctx.getSource())));
    }

    private static int printPing(FabricClientCommandSource source) {
        int ping = getLocalPing();

        if (ping == -1 || source.getClient().isInSingleplayer()) {
            source.sendFeedback(new TranslatableText("commands.cping.local"));
        } else {
            source.sendFeedback(new TranslatableText("commands.cping.multiplayer", ping));
        }

        return 0;
    }

    public static int getLocalPing() {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null)
            return -1;

        PlayerListEntry localPlayer = networkHandler.getPlayerListEntry(networkHandler.getProfile().getId());
        if (localPlayer == null)
            return -1;

        return localPlayer.getLatency();
    }
}
