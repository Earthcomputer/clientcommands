package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class WikiCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwiki")
            .then(argument("page", greedyString())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page")))));
    }

    private static int displayWikiPage(FabricClientCommandSource source, String page) {
        String content = WikiRetriever.getWikiSummary(page);

        if (content == null) {
            source.sendError(new TranslatableText("commands.cwiki.failed"));
            return 0;
        }

        content = content.trim();
        for (String line : content.split("\n")) {
            source.sendFeedback(new LiteralText(line));
        }

        return content.length();
    }

}
