package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import net.earthcomputer.clientcommands.features.SuggestionsHook;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PluginsCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cplugins")
            .executes(ctx -> getPlugins(ctx.getSource()))
            .then(literal("partial")
                .then(argument("partial", greedyString())
                    .executes(ctx -> getPlugins(ctx.getSource(), getString(ctx, "partial")))))
            .then(literal("dispatcher")
                .executes(ctx -> getPluginsFromDispatcher(ctx.getSource()))));
    }

    private static int getPlugins(FabricClientCommandSource source) {
        return getPlugins(source, "");
    }

    private static int getPlugins(FabricClientCommandSource source, String partial) {
        SuggestionsHook.request(partial).whenComplete((suggestions, throwable) -> {
            String plugins = suggestions.getList().stream()
                .map(Suggestion::getText)
                .filter(text -> text.contains(":"))
                .map(text -> text.substring(0, text.indexOf(":")))
                .distinct()
                .collect(Collectors.joining(", "));

            source.sendFeedback(Component.translatable("commands.cplugins.found"));
            source.sendFeedback(Component.literal(plugins));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int getPluginsFromDispatcher(FabricClientCommandSource source) {
        String plugins = source.getClient().getConnection().getCommands().getRoot().getChildren().stream()
            .map(CommandNode::getName)
            .filter(name -> name.contains(":"))
            .map(name -> name.substring(0, name.indexOf(":")))
            .distinct()
            .collect(Collectors.joining(", "));

        source.sendFeedback(Component.translatable("commands.cplugins.found"));
        source.sendFeedback(Component.literal(plugins));
        return Command.SINGLE_SUCCESS;
    }
}
