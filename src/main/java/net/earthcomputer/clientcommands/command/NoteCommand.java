package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;

import static net.earthcomputer.clientcommands.command.arguments.FormattedTextArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class NoteCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cnote")
            .then(argument("message", formattedText())
                .executes(ctx -> note(ctx.getSource(), getFormattedText(ctx, "message")))));
    }

    private static int note(FabricClientCommandSource source, LiteralText message) {
        source.getClient().inGameHud.getChatHud().addMessage(message);
        return 0;
    }
}
