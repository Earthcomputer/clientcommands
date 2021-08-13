package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.TextArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CTitleCommand {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctitle");

        dispatcher.register(literal("ctitle")
            .then(literal("clear")
                .executes(ctx -> executeClear()))
            .then(literal("reset")
                .executes(ctx -> executeReset()))
            .then(literal("title")
                .then(argument("title", text())
                    .executes(ctx -> executeTitle(getTextArgument(ctx, "title")))))
            .then(literal("subtitle")
                .then(argument("title", text())
                    .executes(ctx -> executeSubtitle(getTextArgument(ctx, "title")))))
            .then(literal("actionbar")
                .then(argument("title", text())
                    .executes(ctx -> executeActionBar(getTextArgument(ctx, "title")))))
            .then(literal("times")
                .then(argument("fadeIn", integer(0))
                    .then(argument("stay", integer(0))
                        .then(argument("fadeOut", integer(0))
                            .executes(ctx -> executeTimes(getInteger(ctx, "fadeIn"), getInteger(ctx, "stay"), getInteger(ctx, "fadeOut"))))))));
    }

    private static int executeClear() {
        CLIENT.inGameHud.clearTitle();

        sendFeedback(new TranslatableText("commands.ctitle.cleared"));
        return 1;
    }

    private static int executeReset() {
        CLIENT.inGameHud.clearTitle();
        CLIENT.inGameHud.setDefaultTitleFade();
        
        sendFeedback(new TranslatableText("commands.ctitle.reset"));
        return 1;
    }

    private static int executeTitle(Text title) {
        CLIENT.inGameHud.setTitle(title);

        sendFeedback(new TranslatableText("commands.ctitle.show.title"));
        return 1;
    }

    private static int executeSubtitle(Text title) {
        CLIENT.inGameHud.setSubtitle(title);

        sendFeedback(new TranslatableText("commands.ctitle.show.subtitle"));
        return 1;
    }

    private static int executeActionBar(Text title) {
        CLIENT.inGameHud.setOverlayMessage(title, false);

        sendFeedback(new TranslatableText("commands.ctitle.show.actionbar"));
        return 1;
    }

    private static int executeTimes(int fadeIn, int stay, int fadeOut) {
        CLIENT.inGameHud.setTitleTicks(fadeIn, stay, fadeOut);

        sendFeedback(new TranslatableText("commands.ctitle.times"));
        return 1;
    }
}