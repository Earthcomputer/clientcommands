package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class WikiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cwiki");

        dispatcher.register(literal("cwiki")
            .then(argument("page", greedyString())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page")))));
    }

    private static int displayWikiPage(ServerCommandSource source, String page) {
        String content = WikiRetriever.getWikiSummary(page);

        if (content == null) {
            sendError(new TranslatableText("commands.cwiki.failed"));
            return 0;
        }

        content = content.trim();
        for (String line : content.split("\n")) {
            sendFeedback(new LiteralText(line));
        }

        return content.length();
    }

}
