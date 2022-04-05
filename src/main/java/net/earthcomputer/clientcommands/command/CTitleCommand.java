package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CTextArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CTitleCommand {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctitle")
            .then(literal("clear")
                .executes(ctx -> executeClear()))
            .then(literal("reset")
                .executes(ctx -> executeReset()))
            .then(literal("title")
                .then(argument("title", text())
                    .executes(ctx -> executeTitle(getCTextArgument(ctx, "title")))))
            .then(literal("subtitle")
                .then(argument("title", text())
                    .executes(ctx -> executeSubtitle(getCTextArgument(ctx, "title")))))
            .then(literal("actionbar")
                .then(argument("title", text())
                    .executes(ctx -> executeActionBar(getCTextArgument(ctx, "title")))))
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