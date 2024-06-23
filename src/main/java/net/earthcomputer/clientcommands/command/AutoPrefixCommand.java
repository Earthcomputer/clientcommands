package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class AutoPrefixCommand {
    private static String currentPrefix = "";

    public static String getCurrentPrefix() {
        return currentPrefix;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cautoprefix")
            .then(literal("set")
                .then(literal("tmsg")
                    .executes(ctx -> prefix(ctx.getSource(), "/tm")))
                .then(literal("msg")
                    .then(argument("player", gameProfile(true))
                        .executes(ctx -> prefix(ctx.getSource(), "/w " + getSingleProfileArgument(ctx, "player").getName()))))
                .then(literal("custom")
                    .then(argument("prefix", greedyString())
                        .executes(ctx -> prefix(ctx.getSource(), getString(ctx, "prefix"))))))
            .then(literal("reset")
                .executes(ctx -> clear(ctx.getSource())))
            .then(literal("query")
                .executes(ctx -> current(ctx.getSource())))
        );
    }

    private static int prefix(FabricClientCommandSource source, String prefix) {
        currentPrefix = prefix;
        source.sendFeedback(Component.translatable("commands.cautoprefix.success", currentPrefix));
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(FabricClientCommandSource source) {
        currentPrefix = "";
        source.sendFeedback(Component.translatable("commands.cautoprefix.reset"));
        return Command.SINGLE_SUCCESS;
    }

    private static int current(FabricClientCommandSource source) {
        if (!currentPrefix.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cautoprefix.current", currentPrefix));
        } else {
            source.sendFeedback(Component.translatable("commands.cautoprefix.none"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
