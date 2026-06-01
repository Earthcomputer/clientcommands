package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.ClientCommandFunctions;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class CFunctionCommand {
    private static final SimpleCommandExceptionType NO_ACTIVE_DISPATCHER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cfunction.noActiveDispatcher"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfunction")
            .then(argument("function", greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ClientCommandFunctions.allFunctions(), builder))
                .executes(ctx -> run(ctx.getSource(), getString(ctx, "function")))));
    }

    private static int run(FabricClientCommandSource source, String function) throws CommandSyntaxException {
        CommandDispatcher<FabricClientCommandSource> dispatcher = getActiveDispatcher();
        if (dispatcher == null) {
            throw NO_ACTIVE_DISPATCHER_EXCEPTION.create();
        }
        return ClientCommandFunctions.executeFunction(dispatcher, source, function, result -> source.sendFeedback(Component.translatable("commands.cfunction.success", result, function)));
    }
}
