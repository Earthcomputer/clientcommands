package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.WikiRetriever;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.command.ServerCommandSource;

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
            sendError(new TranslatableComponent("commands.cwiki.failed"));
            return 0;
        }

        content = content.trim();
        for (String line : content.split("\n")) {
            sendFeedback(new TextComponent(line));
        }

        return content.length();
    }

}
