package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.MutableComponent;

import static net.earthcomputer.clientcommands.command.arguments.FormattedTextArgumentType.formattedText;
import static net.earthcomputer.clientcommands.command.arguments.FormattedTextArgumentType.getFormattedText;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NoteCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cnote")
            .then(argument("message", formattedText())
                .executes(ctx -> note(ctx.getSource(), getFormattedText(ctx, "message")))));
    }

    private static int note(FabricClientCommandSource source, MutableComponent message) {
        source.getClient().gui.getChat().addMessage(message);
        return Command.SINGLE_SUCCESS;
    }
}
