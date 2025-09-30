package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CUuidArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CallbackCommand {
    private static final SimpleCommandExceptionType NO_SUCH_CALLBACK_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ccallback.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccallback")
            .then(argument("id", uuid())
                .executes(ctx -> runCallback(getUuid(ctx, "id")))));
    }

    private static int runCallback(UUID uuid) throws CommandSyntaxException {
        if (!CComponentUtil.runCallback(uuid)) {
            throw NO_SUCH_CALLBACK_EXCEPTION.create();
        }

        return Command.SINGLE_SUCCESS;
    }
}
