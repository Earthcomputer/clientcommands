package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class WikiCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cwiki.failed"));

    private static final String WIKI_HOST = "https://minecraft.wiki/";
    private static final String WIKI_ARTICLE = WIKI_HOST + "w/%s";


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwiki")
            .then(argument("page", greedyString())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page")))));
    }

    @Nullable
    private static Component getLinkComponent(String page) {
        String title = WikiRetriever.searchArticleName(page);
        if (title == null) {
            return null;
        }

        String pageName = URLEncoder.encode(title, StandardCharsets.UTF_8).replace('+', '_');
        String url = String.format(WIKI_ARTICLE, pageName);
        URI uri = URI.create(url);

        ClickEvent clickEvent = new ClickEvent.OpenUrl(uri);

        return Component.translatable("commands.cwiki.openArticle")
                .withStyle(style -> style
                        .withClickEvent(clickEvent)
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                );
    }

    private static int displayWikiPage(FabricClientCommandSource source, String page) throws CommandSyntaxException {
        String content = WikiRetriever.getWikiSummary(page);
        if (content == null) {
            throw FAILED_EXCEPTION.create();
        }

        content = content.trim();
        for (String line : content.split("\n")) {
            source.sendFeedback(Component.literal(line));
        }

        Component link = getLinkComponent(page);
        if (link == null) {
            throw FAILED_EXCEPTION.create();
        }

        source.sendFeedback(link);

        return content.length();
    }

}
