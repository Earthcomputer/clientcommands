package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.MutableComponent;

import static net.earthcomputer.clientcommands.command.arguments.FormattedComponentArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class NoteCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cnote")
            .then(argument("message", formattedComponent())
                .executes(ctx -> note(ctx.getSource(), getFormattedComponent(ctx, "message")))));
    }

    private static int note(FabricClientCommandSource source, MutableComponent message) {
        source.getClient().gui.getChat().addMessage(message);
        return Command.SINGLE_SUCCESS;
    }
}
