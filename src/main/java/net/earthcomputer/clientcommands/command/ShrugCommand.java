package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ShrugCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cshrug")
            .executes(ctx -> shrug(ctx.getSource())));
    }

    private static int shrug(FabricClientCommandSource source) {
        source.getPlayer().sendChatMessage("¯\\_(ツ)_/¯");
        return 0;
    }

}
