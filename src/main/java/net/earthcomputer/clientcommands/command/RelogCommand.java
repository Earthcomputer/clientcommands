package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.minecraft.server.command.CommandManager.*;

public class RelogCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.crelog.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("crelog");

        dispatcher.register(literal("crelog")
            .executes(ctx -> relog()));
    }

    private static int relog() throws CommandSyntaxException {
        if (!Relogger.relog()) {
            throw FAILED_EXCEPTION.create();
        }
        return 0;
    }

}
