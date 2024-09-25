package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WikiCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cwiki.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwiki")
            .then(argument("page", greedyString())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page")))));
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

        return content.length();
    }

}
