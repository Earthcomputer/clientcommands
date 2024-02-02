package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getFlag;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class UsageTreeCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.help.failed"));

    @SuppressWarnings("unchecked")
    private static final Flag<UnaryOperator<CommandDispatcher<FabricClientCommandSource>>> FLAG_DISPATCHER =
        Flag.of((Class<UnaryOperator<CommandDispatcher<FabricClientCommandSource>>>) (Class<?>) UnaryOperator.class, "all").withDefaultValue(UnaryOperator.identity()).build();

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cusagetree = dispatcher.register(
            literal("cusagetree")
                .executes(ctx -> usage(ctx.getSource(), getFlag(ctx, FLAG_DISPATCHER).apply(dispatcher)))
                .then(
                    argument("command", greedyString())
                        .suggests((ctx, builder) ->
                            SharedSuggestionProvider.suggest(getFlag(ctx, FLAG_DISPATCHER).apply(dispatcher).getRoot()
                                .getChildren()
                                .stream()
                                .map(CommandNode::getUsageText)
                                .toList(), builder)
                        )
                        .executes(ctx -> usageCommand(ctx.getSource(), getString(ctx, "command"), getFlag(ctx, FLAG_DISPATCHER).apply(dispatcher)))
                )
        );
        FLAG_DISPATCHER.addToCommand(dispatcher, cusagetree, ctx -> d -> (CommandDispatcher<FabricClientCommandSource>) (CommandDispatcher<?>) Objects.requireNonNull(Minecraft.getInstance().getConnection()).getCommands());
    }

    private static int usage(FabricClientCommandSource source, CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var content = tree(dispatcher.getRoot());
        source.sendFeedback(Component.literal("/"));
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
        source.sendFeedback(Component.literal("/" + cmdName).withStyle(s -> s.withColor(node.getCommand() != null ? ChatFormatting.GREEN : ChatFormatting.WHITE)));
        for (var line : content) {
            source.sendFeedback(line);
        }
        return content.size();
    }

    private static List<Component> tree(CommandNode<FabricClientCommandSource> root) {
        List<Component> lines = new ArrayList<>();
        var children = List.copyOf(root.getChildren());
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childName = Component.literal(child.getUsageText()).withStyle(s ->
                s.withColor(child.getCommand() != null ? ChatFormatting.GREEN : ChatFormatting.WHITE)
            );
            var childLines = tree(child);
            if (i + 1 < children.size()) {
                lines.add(Component.literal("├─ ").withStyle(s -> s.withColor(ChatFormatting.GRAY)).append(childName));
                lines.addAll(childLines.stream()
                    .map(line -> Component.literal("│  ").withStyle(s -> s.withColor(ChatFormatting.GRAY)).append(line))
                    .toList());
            } else {
                lines.add(Component.literal("└─ ").withStyle(s -> s.withColor(ChatFormatting.GRAY)).append(childName));
                lines.addAll(childLines.stream().map(line -> Component.literal("   ").append(line)).toList());
            }
        }
        return lines;
    }

}
