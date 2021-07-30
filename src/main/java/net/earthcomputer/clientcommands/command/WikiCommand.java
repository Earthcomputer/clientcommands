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
                .then(argument("page", string())
                        .executes(ctx -> displayWikiContent(ctx.getSource(), getString(ctx, "page"), "summary"))
                        .then(argument("section", string())
                                .executes(ctx -> displayWikiContent(ctx.getSource(), getString(ctx, "page"), getString(ctx, "section"))))));
    }

    private static int displayWikiContent(ServerCommandSource source, String page, String section) {

        String content = null;
        if (section.equalsIgnoreCase("toc")) {
            WikiRetriever.displayWikiTOC(page); // TOC = table of contents
            return 1;

        } else if(section.equalsIgnoreCase("summary")) {
            content = WikiRetriever.getWikiSummary(page);

        } else {
            content = WikiRetriever.getWikiSection(page, section);
        }

        if (content == null) {
            sendError(new TranslatableText("commands.cwiki.failed"));
            return 0;
        }

        content = content.trim();
        for (String line : content.replaceAll("\n{2,}","\n\n").split("\n")) {
            sendFeedback(new LiteralText(line));
        }
        sendFeedback(getViewWikiTOCTextComponent(new TranslatableText("commands.cwiki.viewTOC"), page));
        return content.length();
    }

}
