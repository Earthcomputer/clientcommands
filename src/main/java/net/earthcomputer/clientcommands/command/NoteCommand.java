package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.FormattedTextArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class NoteCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cnote");

        dispatcher.register(literal("cnote")
            .then(argument("message", formattedText())
                .executes(ctx -> note(ctx.getSource(), getFormattedText(ctx, "message")))));
    }

    private static int note(ServerCommandSource source, LiteralText message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
        return 0;
    }
}
