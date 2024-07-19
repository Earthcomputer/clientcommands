package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.xpple.clientarguments.arguments.CMessageArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ReplyCommand {
    private static final SimpleCommandExceptionType NO_TARGET_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.creply.noTargetFound"));
    private static final Dyanmic2ExceptionType MESSAGE_TOO_LONG_EXCEPTION = new Dyanmic2CommandExceptionType((a, b) -> Component.translatable("commands.creply.messageTooLong", a, b));

    @Nullable
    private static String mostRecentWhisper = null;
    @Nullable
    private static String currentTarget = null;

    public static void onChatOpened() {
        currentTarget = mostRecentWhisper;
    }

    public static void setMostRecentWhisper(@NotNull String username) {
        mostRecentWhisper = username;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var command = dispatcher.register(literal("creply")
            .then(argument("message", message())
                .executes(ctx -> reply(ctx.getSource(), getMessage(ctx, "message")))));
        dispatcher.register(literal("cr").redirect(command));
    }

    public static int reply(FabricClientCommandSource source, Component message) throws CommandSyntaxException {
        if (currentTarget == null) {
            throw NO_TARGET_FOUND_EXCEPTION.create();
        }

        source.getClient().getConnection().sendCommand(String.format("w %s %s", currentTarget, message.getString()));

        return Command.SINGLE_SUCCESS;
    }
}
