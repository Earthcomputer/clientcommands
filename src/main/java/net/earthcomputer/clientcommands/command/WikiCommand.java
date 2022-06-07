package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WikiCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwiki")
            .then(argument("page", greedyString())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page")))));
    }

    private static int displayWikiPage(FabricClientCommandSource source, String page) {
        String content = WikiRetriever.getWikiSummary(page);

        if (content == null) {
            source.sendError(Text.translatable("commands.cwiki.failed"));
            return 0;
        }

        content = content.trim();
        for (String line : content.split("\n")) {
            source.sendFeedback(Text.literal(line));
        }

        return content.length();
    }

}
