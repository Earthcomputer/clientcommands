package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandSource;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;

public class ShrugCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        addClientSideCommand("cshrug");

        dispatcher.register(literal("cshrug")
            .executes(ctx -> shrug()));
    }

    private static int shrug() {
        MinecraftClient.getInstance().player.sendChatMessage("¯\\_(ツ)_/¯");
        return 0;
    }

}
