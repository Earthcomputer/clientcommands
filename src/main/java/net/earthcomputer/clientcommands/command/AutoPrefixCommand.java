package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static dev.xpple.clientarguments.arguments.CComponentArgument.getComponent;
import static dev.xpple.clientarguments.arguments.CComponentArgument.textComponent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutoPrefixCommand {
    private static String currentPrefix = null;

    public static String getCurrentPrefix() {
        return currentPrefix;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cautoprefix")
                .then(literal("set").then(argument("prefix", textComponent(context))
                        .executes(ctx -> prefix(ctx.getSource(), getComponent(ctx, "prefix")))))
                .then(literal("clear"))
                    .executes(ctx -> clear(ctx.getSource())));
    }

    private static int prefix(FabricClientCommandSource source, Component message) {
        currentPrefix = message.getString();
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(FabricClientCommandSource source) {
        currentPrefix = null;
        return Command.SINGLE_SUCCESS;
    }
}
