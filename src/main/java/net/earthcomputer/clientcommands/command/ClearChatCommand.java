package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ClearChatCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cclearchat")
            .executes(ctx -> clearChat(ctx.getSource()))
            .then(argument("clearHistory", bool())
                .executes(ctx -> clearChat(ctx.getSource(), getBool(ctx, "clearHistory")))));
    }

    private static int clearChat(FabricClientCommandSource source) {
        return clearChat(source, false);
    }

    private static int clearChat(FabricClientCommandSource source, boolean clearHistory) {
        source.getClient().inGameHud.getChatHud().clear(clearHistory);
        return Command.SINGLE_SUCCESS;
    }
}
