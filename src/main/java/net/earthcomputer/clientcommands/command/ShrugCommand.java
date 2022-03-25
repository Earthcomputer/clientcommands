package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class ShrugCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cshrug")
            .executes(ctx -> shrug()));
    }

    private static int shrug() {
        MinecraftClient.getInstance().player.sendChatMessage("¯\\_(ツ)_/¯");
        return 0;
    }

}
