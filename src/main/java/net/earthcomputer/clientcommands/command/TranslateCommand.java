package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.command.arguments.TranslationQueryArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;

import static net.earthcomputer.clientcommands.command.arguments.TranslationQueryArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TranslateCommand {

    private static final SimpleCommandExceptionType UNKNOWN_ERROR_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ctranslate.unknownError"));

    private static final String url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final Duration DURATION = Duration.ofSeconds(5);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctranslate")
                .then(argument("query", translationQuery())
                        .executes(ctx -> translate(ctx.getSource(), getTranslationQuery(ctx, "query")))));
    }

    private static int translate(FabricClientCommandSource source, TranslationQueryArgumentType.TranslationQuery query) throws CommandSyntaxException {
        try {
            HttpRequest request = HttpRequest.newBuilder(createUri(query.from(), query.to(), query.query()))
                    .timeout(DURATION)
                    .GET()
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(response -> source.getClient().send(() -> {
                        JsonArray result = JsonParser.parseString(response).getAsJsonArray();
                        source.sendFeedback(Text.of(result.get(0).getAsJsonArray().get(0).getAsJsonArray().get(0).getAsString()));
                    }));
        } catch (Exception e) {
            throw UNKNOWN_ERROR_EXCEPTION.create();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static URI createUri(String from, String to, String query) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(url, Charset.defaultCharset());
        builder.addParameter("sl", from);
        builder.addParameter("tl", to);
        builder.addParameter("q", query);
        return builder.build();
    }
}
