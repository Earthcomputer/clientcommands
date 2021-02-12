package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class DabCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cdab");

        dispatcher.register(literal("cdab").executes(ctx -> shrug()));
    }

    private static int shrug() {
        MinecraftClient.getInstance().player.sendChatMessage("\\(@^0^@)/");
        return 0;
    }

}
