package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CComponentArgument.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTitleCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("ctitle")
            .then(literal("clear")
                .executes(ctx -> executeClear(ctx.getSource())))
            .then(literal("reset")
                .executes(ctx -> executeReset(ctx.getSource())))
            .then(literal("title")
                .then(argument("title", textComponent(context))
                    .executes(ctx -> executeTitle(ctx.getSource(), getComponent(ctx, "title")))))
            .then(literal("subtitle")
                .then(argument("title", textComponent(context))
                    .executes(ctx -> executeSubtitle(ctx.getSource(), getComponent(ctx, "title")))))
            .then(literal("actionbar")
                .then(argument("title", textComponent(context))
                    .executes(ctx -> executeActionBar(ctx.getSource(), getComponent(ctx, "title")))))
            .then(literal("times")
                .then(argument("fadeIn", integer(0))
                    .then(argument("stay", integer(0))
                        .then(argument("fadeOut", integer(0))
                            .executes(ctx -> executeTimes(ctx.getSource(), getInteger(ctx, "fadeIn"), getInteger(ctx, "stay"), getInteger(ctx, "fadeOut"))))))));
    }

    private static int executeClear(FabricClientCommandSource source) {
        source.getClient().gui.clear();

        source.sendFeedback(Component.translatable("commands.ctitle.cleared"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReset(FabricClientCommandSource source) {
        source.getClient().gui.clear();
        source.getClient().gui.resetTitleTimes();

        source.sendFeedback(Component.translatable("commands.ctitle.reset"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTitle(FabricClientCommandSource source, Component title) {
        source.getClient().gui.setTitle(title);

        sendFeedback(Component.translatable("commands.ctitle.show.title"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSubtitle(FabricClientCommandSource source, Component title) {
        source.getClient().gui.setSubtitle(title);

        sendFeedback(Component.translatable("commands.ctitle.show.subtitle"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeActionBar(FabricClientCommandSource source, Component title) {
        source.getClient().gui.setOverlayMessage(title, false);

        sendFeedback(Component.translatable("commands.ctitle.show.actionbar"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimes(FabricClientCommandSource source, int fadeIn, int stay, int fadeOut) {
        source.getClient().gui.setTimes(fadeIn, stay, fadeOut);

        sendFeedback(Component.translatable("commands.ctitle.times"));
        return Command.SINGLE_SUCCESS;
    }
}