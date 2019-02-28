package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandSource;
import net.minecraft.text.StringTextComponent;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;

public class NoteCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        addClientSideCommand("cnote");

        dispatcher.register(literal("cnote")
            .then(argument("message", greedyString())
                .executes(ctx -> note(getString(ctx, "message")))));
    }

    private static int note(String message) {
        MinecraftClient.getInstance().inGameHud.getHudChat().addMessage(new StringTextComponent(message));
        return 0;
    }

}
