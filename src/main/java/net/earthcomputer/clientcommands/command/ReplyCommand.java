package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.Configs;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.xpple.clientarguments.arguments.CMessageArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ReplyCommand {
    public static final float MAXIMUM_REPLY_DELAY_SECONDS = 300.0f;

    private static final SimpleCommandExceptionType NO_TARGET_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.creply.noTargetFound"));
    private static final Dynamic2CommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("commands.creply.messageTooLong", a, b));

    private static final List<ReplyCandidate> replyCandidates = new ArrayList<>();

    @Nullable
    public static String getCurrentTarget() {
        long now = System.currentTimeMillis();

        for (int i = 0; i < replyCandidates.size(); i++) {
            ReplyCandidate candidate = replyCandidates.get(i);
            if (now - candidate.timestampMs > MAXIMUM_REPLY_DELAY_SECONDS * 1_000.0f) {
                replyCandidates.remove(i--);
            } else {
                // list is ordered and `now - candidate.timestampMs` will only get smaller and smaller, so the cmp above will never change
                break;
            }
        }

        for (int i = replyCandidates.size() - 1; i >= 0; i--) {
            ReplyCandidate candidate = replyCandidates.get(i);
            if (now - candidate.timestampMs >= Configs.minimumReplyDelaySeconds * 1_000.0f) {
                return candidate.username;
            }
        }

        return null;
    }

    public static void addReplyCandidate(String username, long timestamp) {
        replyCandidates.add(new ReplyCandidate(username, timestamp));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var command = dispatcher.register(literal("creply")
            .then(argument("message", message())
                .executes(ctx -> reply(ctx.getSource(), getMessage(ctx, "message")))));
        dispatcher.register(literal("cr").redirect(command));
    }

    public static int reply(FabricClientCommandSource source, Component message) throws CommandSyntaxException {
        String target = ReplyCommand.getCurrentTarget();
        if (target == null) {
            throw NO_TARGET_FOUND_EXCEPTION.create();
        }

        String text = message.getString();
        String command = String.format("w %s %s", target, text);

        if (command.length() > SharedConstants.MAX_CHAT_LENGTH) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(SharedConstants.MAX_CHAT_LENGTH - (command.length() - text.length()), text.length());
        }

        source.getClient().getConnection().sendCommand(command);

        return Command.SINGLE_SUCCESS;
    }

    private record ReplyCandidate(String username, long timestampMs) {
    }
}
