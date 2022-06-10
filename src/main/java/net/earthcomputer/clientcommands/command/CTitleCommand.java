package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CTextArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTitleCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctitle")
            .then(literal("clear")
                .executes(ctx -> executeClear(ctx.getSource())))
            .then(literal("reset")
                .executes(ctx -> executeReset(ctx.getSource())))
            .then(literal("title")
                .then(argument("title", text())
                    .executes(ctx -> executeTitle(ctx.getSource(), getCTextArgument(ctx, "title")))))
            .then(literal("subtitle")
                .then(argument("title", text())
                    .executes(ctx -> executeSubtitle(ctx.getSource(), getCTextArgument(ctx, "title")))))
            .then(literal("actionbar")
                .then(argument("title", text())
                    .executes(ctx -> executeActionBar(ctx.getSource(), getCTextArgument(ctx, "title")))))
            .then(literal("times")
                .then(argument("fadeIn", integer(0))
                    .then(argument("stay", integer(0))
                        .then(argument("fadeOut", integer(0))
                            .executes(ctx -> executeTimes(ctx.getSource(), getInteger(ctx, "fadeIn"), getInteger(ctx, "stay"), getInteger(ctx, "fadeOut"))))))));
    }

    private static int executeClear(FabricClientCommandSource source) {
        source.getClient().inGameHud.clearTitle();

        source.sendFeedback(Text.translatable("commands.ctitle.cleared"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReset(FabricClientCommandSource source) {
        source.getClient().inGameHud.clearTitle();
        source.getClient().inGameHud.setDefaultTitleFade();
        
        source.sendFeedback(Text.translatable("commands.ctitle.reset"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTitle(FabricClientCommandSource source, Text title) {
        source.getClient().inGameHud.setTitle(title);

        sendFeedback(Text.translatable("commands.ctitle.show.title"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSubtitle(FabricClientCommandSource source, Text title) {
        source.getClient().inGameHud.setSubtitle(title);

        sendFeedback(Text.translatable("commands.ctitle.show.subtitle"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeActionBar(FabricClientCommandSource source, Text title) {
        source.getClient().inGameHud.setOverlayMessage(title, false);

        sendFeedback(Text.translatable("commands.ctitle.show.actionbar"));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimes(FabricClientCommandSource source, int fadeIn, int stay, int fadeOut) {
        source.getClient().inGameHud.setTitleTicks(fadeIn, stay, fadeOut);

        sendFeedback(Text.translatable("commands.ctitle.times"));
        return Command.SINGLE_SUCCESS;
    }
}