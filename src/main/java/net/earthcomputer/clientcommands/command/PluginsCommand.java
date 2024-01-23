package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.text.Text;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

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
        source.getClient().getNetworkHandler().sendPacket(new RequestCommandCompletionsC2SPacket(1, partial));
        callback = packet -> {
            String plugins = packet.getSuggestions().getList().stream()
                .map(Suggestion::getText)
                .filter(text -> text.contains(":"))
                .map(text -> text.substring(0, text.indexOf(":")))
                .distinct()
                .collect(Collectors.joining(", "));

            source.sendFeedback(Text.translatable("commands.cplugins.found"));
            source.sendFeedback(Text.of(plugins));
        };
        return Command.SINGLE_SUCCESS;
    }

    private static int getPluginsFromDispatcher(FabricClientCommandSource source) {
        String plugins = source.getClient().getNetworkHandler().getCommandDispatcher().getRoot().getChildren().stream()
            .map(CommandNode::getName)
            .filter(name -> name.contains(":"))
            .map(name -> name.substring(0, name.indexOf(":")))
            .distinct()
            .collect(Collectors.joining(", "));

        source.sendFeedback(Text.translatable("commands.cplugins.found"));
        source.sendFeedback(Text.of(plugins));
        return Command.SINGLE_SUCCESS;
    }

    public static void onCommandSuggestions(CommandSuggestionsS2CPacket packet) {
        if (!awaitingSuggestionsPacket) {
            return;
        }
        awaitingSuggestionsPacket = false;
        callback.apply(packet);
    }
}

@FunctionalInterface
interface SuggestionsCallback {
    void apply(CommandSuggestionsS2CPacket packet);
}
