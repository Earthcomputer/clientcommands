package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class UsageTreeCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.helptree.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cusagetree");

        dispatcher.register(
            literal("cusagetree")
                .executes(ctx -> {
                    var content = tree(dispatcher.getRoot());
                    sendFeedback(new LiteralText("/"));
                    for (var line : content) {
                        sendFeedback(line);
                    }
                    return content.size() + 1;
                })
                .then(argument("command", greedyString())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(dispatcher.getRoot().getChildren().stream().map(CommandNode::getUsageText).toList(), builder))
                    .executes(ctx -> {
                        String cmdName = getString(ctx, "command");
                        var parseResults = dispatcher.parse(cmdName, ctx.getSource());
                        if (parseResults.getContext().getNodes().isEmpty()) {
                            throw FAILED_EXCEPTION.create();
                        }
                        var content = tree(Iterables.getLast(parseResults.getContext().getNodes()).getNode());
                        sendFeedback(new LiteralText("/" + cmdName));
                        for (var line : content) {
                            sendFeedback(line);
                        }
                        return content.size() + 1;
                    })
                )
        );
    }

    public static List<Text> tree(CommandNode<?> root) {
        List<Text> lines = new ArrayList<>();
        var children = List.copyOf(root.getChildren());
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childName = new LiteralText(child.getUsageText()).styled(s -> s.withColor(child.getCommand() != null ? Formatting.GREEN : Formatting.WHITE));
            var childLines = tree(child);
            if (i + 1 < children.size()) {
                lines.add(new LiteralText("├─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream().map(line -> new LiteralText("│  ").styled(s -> s.withColor(Formatting.GRAY)).append(line)).toList());
            } else {
                lines.add(new LiteralText("└─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream().map(line -> new LiteralText("   ").append(line)).toList());
            }
        }
        return lines;
    }
}
