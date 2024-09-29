package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.xpple.clientarguments.arguments.CMessageArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ReplyCommand {
    private static final SimpleCommandExceptionType NO_TARGET_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.creply.noTargetFound"));
    private static final Dynamic2CommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("commands.creply.messageTooLong", a, b));

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

        String text = message.getString();
        String command = String.format("w %s %s", currentTarget, text);

        if (command.length() > 256) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(SharedConstants.MAX_CHAT_LENGTH - (command.length() - text.length()), text.length());
        }

        source.getClient().getConnection().sendCommand(command);

        return Command.SINGLE_SUCCESS;
    }
}
