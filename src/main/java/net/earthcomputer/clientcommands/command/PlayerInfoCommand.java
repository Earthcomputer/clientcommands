package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.*;

public class PlayerInfoCommand {

    private static final Map<String, List<String>> cacheByName = new HashMap<>();
    private static final Map<String, List<String>> cacheByUuid = new HashMap<>();

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final Duration DURATION = Duration.ofSeconds(5);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> cplayerinfo = dispatcher.register(literal("cplayerinfo"));
        dispatcher.register(literal("cplayerinfo")
                .then(literal("namehistory")
                        .then(argument("player", string())
                                .suggests((context, builder) -> suggestMatching(context.getSource().getPlayerNames(), builder))
                                .executes(ctx -> getNameHistory(ctx.getSource(), getString(ctx, "player"))))));
    }

    private static int getNameHistory(FabricClientCommandSource source, String player) {
        if (player.length() >= 32) {
            if (cacheByUuid.containsKey(player)) {
                source.sendFeedback(Text.translatable("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", cacheByUuid.get(player))));
            } else {
                fetchNameHistory(source, player);
            }
        } else {
            if (cacheByName.containsKey(player)) {
                source.sendFeedback(Text.translatable("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", cacheByName.get(player))));
            } else {
                if (source.getClient().isInSingleplayer()) {
                    ServerPlayerEntity playerEntity = source.getClient().getServer().getPlayerManager().getPlayer(player);
                    if (playerEntity == null) {
                        getNameHistoryByName(source, player);
                    } else {
                        fetchNameHistory(source, playerEntity.getUuidAsString());
                    }
                } else {
                    PlayerListEntry playerListEntry = source.getClient().getNetworkHandler().getPlayerListEntry(player);
                    if (playerListEntry == null) {
                        getNameHistoryByName(source, player);
                    } else {
                        fetchNameHistory(source, playerListEntry.getProfile().getId().toString());
                    }
                }
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void getNameHistoryByName(FabricClientCommandSource source, String player) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + player))
                .timeout(DURATION)
                .GET()
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> source.getClient().send(() -> {
                    JsonElement result = JsonParser.parseString(response);
                    if (result instanceof JsonNull) {
                        source.sendError(Text.translatable("commands.cplayerinfo.ioException"));
                    } else {
                        fetchNameHistory(source, result.getAsJsonObject().get("id").getAsString());
                    }
                }));
    }

    private static void fetchNameHistory(FabricClientCommandSource source, String uuid) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/user/profiles/" + uuid + "/names"))
                .timeout(DURATION)
                .GET()
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> source.getClient().send(() -> {
                    JsonElement result = JsonParser.parseString(response);
                    if (result.isJsonArray()) {
                        JsonArray array = result.getAsJsonArray();
                        List<String> names = new ArrayList<>();
                        array.forEach(name -> names.add(name.getAsJsonObject().get("name").getAsString()));
                        String player = names.get(names.size() - 1);
                        cacheByName.put(player, names);
                        cacheByUuid.put(uuid, names);
                        source.sendFeedback(Text.translatable("commands.cplayerinfo.getNameHistory.success", player, String.join(", ", names)));
                    } else {
                        source.sendError(Text.translatable("commands.cplayerinfo.ioException"));
                    }
                }));
    }
}
