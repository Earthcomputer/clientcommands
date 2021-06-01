package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.*;

public class PlayerInfoCommand {

    private static final SimpleCommandExceptionType IO_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cnamehistory.ioException"));

    private static final Map<String, List<String>> cacheByName = new HashMap<>();
    private static final Map<String, List<String>> cacheByUuid = new HashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cplayerinfo");

        LiteralCommandNode<ServerCommandSource> cplayerinfo = dispatcher.register(literal("cplayerinfo"));
        dispatcher.register(literal("cplayerinfo")
                .then(literal("namehistory")
                        .then(argument("player", string())
                                .suggests(((context, builder) -> suggestMatching(MinecraftClient.getInstance().world.getPlayers().stream().map(abstractPlayer -> abstractPlayer.getName().getString()), builder)))
                                .executes(ctx -> getNameHistory(ctx.getSource(), getString(ctx, "player"))))));
    }

    private static int getNameHistory(ServerCommandSource source, String player) throws CommandSyntaxException {
        String uuid;
        if (player.contains("-")) {
            uuid = player;
            if (cacheByUuid.containsKey(uuid)) {
                sendFeedback(new TranslatableText("commands.cnamehistory.success", player, String.join(", ", cacheByUuid.get(uuid))));
                return 0;
            }
        } else {
            if (cacheByName.containsKey(player)) {
                sendFeedback(new TranslatableText("commands.cnamehistory.success", player, String.join(", ", cacheByName.get(player))));
                return 0;
            }
            Optional<String> optional = MinecraftClient.getInstance().world.getPlayers().stream()
                    .filter(abstractPlayer -> abstractPlayer.getName().getString().equals(player))
                    .map(Entity::getUuidAsString)
                    .findFirst();
            if (optional.isPresent()) {
                uuid = optional.get();
            } else {
                uuid = requestAsync("https://api.mojang.com/users/profiles/minecraft/" + player).getAsJsonObject().get("id").getAsString();
            }
        }
        JsonArray names = requestAsync("https://api.mojang.com/user/profiles/" + uuid + "/names").getAsJsonArray();

        List<String> stringNames = new ArrayList<>();
        names.forEach(name -> stringNames.add(name.getAsJsonObject().get("name").getAsString()));
        cacheByName.put(player, stringNames);
        cacheByUuid.put(uuid, stringNames);
        sendFeedback(new TranslatableText("commands.cnamehistory.success", player, String.join(", ", stringNames)));
        return 0;
    }

    private static JsonElement requestAsync(String url) throws CommandSyntaxException {
        CompletableFuture<JsonElement> future = CompletableFuture.supplyAsync(() -> {
            try {
                URLConnection request = new URL(url).openConnection();
                request.connect();
                return (new JsonParser().parse(new InputStreamReader(request.getInputStream())));
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
        try {
            return future.join();
        } catch (Exception e) {
            throw IO_EXCEPTION.create();
        }
    }
}
