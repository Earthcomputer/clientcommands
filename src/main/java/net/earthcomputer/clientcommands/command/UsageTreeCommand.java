package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class UsageTreeCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.help.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("cusagetree")
                .executes(ctx -> usage(ctx, dispatcher))
                .then(
                    argument("command", greedyString())
                        .suggests((ctx, builder) ->
                            CommandSource.suggestMatching(dispatcher.getRoot()
                                .getChildren()
                                .stream()
                                .map(CommandNode::getUsageText)
                                .toList(), builder)
                        )
                        .executes(ctx -> usageCommand(ctx, dispatcher))
                )
        );
    }

    private static int usage(CommandContext<FabricClientCommandSource> ctx, CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var content = tree(dispatcher.getRoot());
        sendFeedback(new LiteralText("/"));
        for (var line : content) {
            sendFeedback(line);
        }
        return content.size() + 1;
    }

    private static int usageCommand(CommandContext<FabricClientCommandSource> ctx, CommandDispatcher<FabricClientCommandSource> dispatcher) throws CommandSyntaxException {
        String cmdName = getString(ctx, "command");
        var parseResults = dispatcher.parse(cmdName, ctx.getSource());
        if (parseResults.getContext().getNodes().isEmpty()) {
            throw FAILED_EXCEPTION.create();
        }
        var node = Iterables.getLast(parseResults.getContext().getNodes()).getNode();
        var content = tree(node);
        sendFeedback(new LiteralText("/" + cmdName).styled(s -> s.withColor(node.getCommand() != null ? Formatting.GREEN : Formatting.WHITE)));
        for (var line : content) {
            sendFeedback(line);
        }
        return content.size() + 1;
    }

    private static List<Text> tree(CommandNode<FabricClientCommandSource> root) {
        List<Text> lines = new ArrayList<>();
        var children = List.copyOf(root.getChildren());
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childName = new LiteralText(child.getUsageText()).styled(s ->
                s.withColor(child.getCommand() != null ? Formatting.GREEN : Formatting.WHITE)
            );
            var childLines = tree(child);
            if (i + 1 < children.size()) {
                lines.add(new LiteralText("├─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream()
                    .map(line -> new LiteralText("│  ").styled(s -> s.withColor(Formatting.GRAY)).append(line))
                    .toList());
            } else {
                lines.add(new LiteralText("└─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream().map(line -> new LiteralText("   ").append(line)).toList());
            }
        }
        return lines;
    }

}
