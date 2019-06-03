package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.network.chat.TextComponent;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class NoteCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cnote");

        dispatcher.register(literal("cnote")
            .then(argument("message", greedyString())
                .executes(ctx -> note(getString(ctx, "message")))));
    }

    private static int note(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new TextComponent(message));
        return 0;
    }

}
