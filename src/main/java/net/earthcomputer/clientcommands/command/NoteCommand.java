package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.MutableComponent;

import static net.earthcomputer.clientcommands.command.arguments.ExtendedMarkdownArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class NoteCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cnote")
            .then(argument("message", extendedMarkdown())
                .executes(ctx -> note(getExtendedMarkdown(ctx, "message")))));
    }

    private static int note(MutableComponent message) {
        ClientCommandHelper.sendFeedback(message);
        return Command.SINGLE_SUCCESS;
    }
}
