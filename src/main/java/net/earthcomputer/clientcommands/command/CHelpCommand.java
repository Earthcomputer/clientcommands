package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CHelpCommand {

    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.help.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("chelp");

        dispatcher.register(literal("chelp")
            .executes(ctx -> {
                int cmdCount = 0;
                for (var command : dispatcher.getRoot().getChildren()) {
                    String cmdName = command.getName();
                    if (isClientSideCommand(cmdName)) {
                        var usage = dispatcher.getSmartUsage(command, ctx.getSource());
                        for (String u : usage.values()) {
                            sendFeedback(new LiteralText("/" + cmdName + " " + u));
                        }
                        cmdCount += usage.size();
                        if (usage.size() == 0) {
                            sendFeedback(new LiteralText("/" + cmdName));
                            cmdCount++;
                        }
                    }
                }
                return cmdCount;
            })
            .then(argument("command", greedyString())
                .executes(ctx -> {
                    String cmdName = getString(ctx, "command");
                    if (!isClientSideCommand(cmdName))
                        throw FAILED_EXCEPTION.create();

                    var parseResults = dispatcher.parse(cmdName, ctx.getSource());
                    if (parseResults.getContext().getNodes().isEmpty())
                        throw FAILED_EXCEPTION.create();

                    var usage = dispatcher.getSmartUsage(Iterables.getLast(parseResults.getContext().getNodes()).getNode(), ctx.getSource());
                    for (String u : usage.values()) {
                        sendFeedback(new LiteralText("/" + cmdName + " " + u));
                    }

                    return usage.size();
                })));
    }

}
