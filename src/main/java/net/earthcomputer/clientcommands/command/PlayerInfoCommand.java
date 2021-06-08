package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.*;

public class PlayerInfoCommand {

    private static final Map<String, List<String>> cacheByName = new HashMap<>();
    private static final Map<String, List<String>> cacheByUuid = new HashMap<>();

    private static final JsonParser parser = new JsonParser();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cplayerinfo");

        LiteralCommandNode<ServerCommandSource> cplayerinfo = dispatcher.register(literal("cplayerinfo"));
        dispatcher.register(literal("cplayerinfo")
                .then(literal("namehistory")
                        .then(argument("player", string())
                                .suggests(((context, builder) -> suggestMatching(MinecraftClient.getInstance().world.getPlayers().stream().map(abstractPlayer -> abstractPlayer.getName().getString()), builder)))
                                .executes(ctx -> getNameHistory(ctx.getSource(), getString(ctx, "player"))))));
    }

    private static int getNameHistory(ServerCommandSource source, String player) {
        if (player.length() >= 32) {
            if (cacheByUuid.containsKey(player)) {
                sendFeedback(new TranslatableText("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", cacheByUuid.get(player))));
            } else {
                fetchNameHistory(player);
            }
        } else {
            if (cacheByName.containsKey(player)) {
                sendFeedback(new TranslatableText("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", cacheByName.get(player))));
            } else {
                Optional<String> optional = MinecraftClient.getInstance().world.getPlayers().stream()
                        .filter(abstractPlayer -> abstractPlayer.getName().getString().equals(player))
                        .map(Entity::getUuidAsString)
                        .findFirst();
                if (optional.isPresent()) {
                    fetchNameHistory(optional.get());
                } else {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + player))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenAccept(response -> {
                                JsonElement result = parser.parse(response);
                                if (result instanceof JsonNull) {
                                    sendError(new TranslatableText("commands.cplayerinfo.ioException"));
                                } else {
                                    fetchNameHistory(result.getAsJsonObject().get("id").getAsString());
                                }
                            });
                }
            }
        }
        return 0;
    }

    private static void fetchNameHistory(String uuid) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/user/profiles/" + uuid + "/names"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> {
                    JsonElement result = parser.parse(response);
                    if (result.isJsonArray()) {
                        JsonArray array = result.getAsJsonArray();
                        List<String> names = new ArrayList<>();
                        array.forEach(name -> names.add(name.getAsJsonObject().get("name").getAsString()));
                        String player = names.get(names.size() - 1);
                        cacheByName.put(player, names);
                        cacheByUuid.put(uuid, names);
                        sendFeedback(new TranslatableText("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", names)));
                    } else {
                        sendError(new TranslatableText("commands.cplayerinfo.ioException"));
                    }
                });
    }
}
