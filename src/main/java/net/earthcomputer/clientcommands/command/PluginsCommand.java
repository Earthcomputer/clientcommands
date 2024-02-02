package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PluginsCommand {

    private static boolean awaitingSuggestionsPacket = false;

    private static SuggestionsCallback callback;

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
        awaitingSuggestionsPacket = true;
        source.getClient().getConnection().send(new ServerboundCommandSuggestionPacket(1, partial));
        callback = packet -> {
            String plugins = packet.getSuggestions().getList().stream()
                .map(Suggestion::getText)
                .filter(text -> text.contains(":"))
                .map(text -> text.substring(0, text.indexOf(":")))
                .distinct()
                .collect(Collectors.joining(", "));

            source.sendFeedback(Component.translatable("commands.cplugins.found"));
            source.sendFeedback(Component.literal(plugins));
        };
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

    public static void onCommandSuggestions(ClientboundCommandSuggestionsPacket packet) {
        if (!awaitingSuggestionsPacket) {
            return;
        }
        awaitingSuggestionsPacket = false;
        callback.apply(packet);
    }
}

@FunctionalInterface
interface SuggestionsCallback {
    void apply(ClientboundCommandSuggestionsPacket packet);
}
