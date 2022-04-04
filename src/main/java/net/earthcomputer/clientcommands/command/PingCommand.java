package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class PingCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cping")
            .executes(ctx -> printPing()));
    }

    private static int printPing() {
        MinecraftClient instance = MinecraftClient.getInstance();
        int ping = FishingCracker.getLocalPing();

        if (ping == -1 || instance.isInSingleplayer()) {
            sendFeedback(new TranslatableText("commands.cping.local"));
        } else {
            sendFeedback(new TranslatableText("commands.cping.multiplayer", ping));
        }

        return 0;
    }
}
