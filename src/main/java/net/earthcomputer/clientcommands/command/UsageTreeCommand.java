package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class UsageTreeCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.help.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("cusagetree")
                .executes(ctx -> usage(ctx.getSource(), dispatcher))
                .then(
                    argument("command", greedyString())
                        .suggests((ctx, builder) ->
                            CommandSource.suggestMatching(dispatcher.getRoot()
                                .getChildren()
                                .stream()
                                .map(CommandNode::getUsageText)
                                .toList(), builder)
                        )
                        .executes(ctx -> usageCommand(ctx.getSource(), getString(ctx, "command"), dispatcher))
                )
        );
    }

    private static int usage(FabricClientCommandSource source, CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var content = tree(dispatcher.getRoot());
        source.sendFeedback(Text.literal("/"));
        for (var line : content) {
            source.sendFeedback(line);
        }
        return content.size();
    }

    private static int usageCommand(FabricClientCommandSource source, String cmdName, CommandDispatcher<FabricClientCommandSource> dispatcher) throws CommandSyntaxException {
        var parseResults = dispatcher.parse(cmdName, source);
        if (parseResults.getContext().getNodes().isEmpty()) {
            throw FAILED_EXCEPTION.create();
        }
        var node = Iterables.getLast(parseResults.getContext().getNodes()).getNode();
        var content = tree(node);
        source.sendFeedback(Text.literal("/" + cmdName).styled(s -> s.withColor(node.getCommand() != null ? Formatting.GREEN : Formatting.WHITE)));
        for (var line : content) {
            source.sendFeedback(line);
        }
        return content.size();
    }

    private static List<Text> tree(CommandNode<FabricClientCommandSource> root) {
        List<Text> lines = new ArrayList<>();
        var children = List.copyOf(root.getChildren());
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childName = Text.literal(child.getUsageText()).styled(s ->
                s.withColor(child.getCommand() != null ? Formatting.GREEN : Formatting.WHITE)
            );
            var childLines = tree(child);
            if (i + 1 < children.size()) {
                lines.add(Text.literal("├─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream()
                    .map(line -> Text.literal("│  ").styled(s -> s.withColor(Formatting.GRAY)).append(line))
                    .toList());
            } else {
                lines.add(Text.literal("└─ ").styled(s -> s.withColor(Formatting.GRAY)).append(childName));
                lines.addAll(childLines.stream().map(line -> Text.literal("   ").append(line)).toList());
            }
        }
        return lines;
    }

}
