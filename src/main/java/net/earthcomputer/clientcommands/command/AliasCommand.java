package net.earthcomputer.clientcommands.command;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.BrigadierRemover;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashMap;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class AliasCommand {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Path aliasPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands").resolve("alias_list.json");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("calias");

        dispatcher.register(literal("calias")
            .then(literal("add")
                .then(argument("key", string())
                    .then(argument("command", greedyString())
                        .executes(ctx -> addAlias(getString(ctx, "key"), getString(ctx, "command"))))))
            .then(literal("list")
                .executes(ctx -> listAliases()))
            .then(literal("remove")
                .then(argument("key", string())
                    .executes(ctx -> removeAlias(getString(ctx, "key"))))));

        HashMap<String, String> aliasMap = getAliases();

        if (aliasMap == null || aliasMap.isEmpty()) {
            sendError(new LiteralText("No aliases registered"));
        } else {
            for(String key: aliasMap.keySet()) {
                addClientSideCommand(key);
                dispatcher.register(literal(key)
                        .executes(ctx -> executeAliasCommand(key, null))
                        .then(argument("arguments", greedyString())
                                .executes(ctx -> executeAliasCommand(key, getString(ctx, "arguments")))));
            }
        }

    }
    private static int executeAliasCommand(String aliasKey, String arguments) {

        // TODO: add support for optional greedy text arguments(?)
        HashMap<String, String> aliasMap = getAliases();
        String cmd;
        if(aliasMap.containsKey(aliasKey)) {
            cmd = aliasMap.get(aliasKey);
        } else {
            sendError(new LiteralText("No such key exists"));
            return 0;
        }

        if(arguments!=null) {
            cmd = String.format(cmd, arguments.split(" "));
        }
        MinecraftClient.getInstance().player.sendChatMessage(cmd);

        return 0;
    }

    private static int addAlias(String key, String command) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        var dispatcher = (CommandDispatcher<ServerCommandSource>) (CommandDispatcher<?>) networkHandler.getCommandDispatcher();

        HashMap<String, String> aliasMap = getAliases();

        assert aliasMap != null;
        if(aliasMap.containsKey(key)) {
            sendError(new LiteralText("That key already exists!"));
            return 0;
        }
        addClientSideCommand(key);

        dispatcher.register(literal(key)
                .executes(ctx -> executeAliasCommand(key, null))
                .then(argument("arguments", greedyString())
                        .executes(ctx -> executeAliasCommand(key, getString(ctx, "arguments")))));
        aliasMap.put(key, command);

        try {
            Writer writer = new FileWriter(String.valueOf(aliasPath));
            Gson gson = new Gson();

            gson.toJson(aliasMap, writer);
            writer.flush();
            writer.close();

            sendFeedback(new LiteralText("New alias added successfully!"));
        } catch (Exception e) {
            sendError(new LiteralText("Uh oh"));
        }
        return 0;
    }
    private static int listAliases() {
        HashMap<String, String> aliasMap = getAliases();
        if (aliasMap == null || aliasMap.isEmpty()) {
            sendError(new LiteralText("No aliases registered"));
        } else {
            sendFeedback(new LiteralText("Aliases registered: " + aliasMap.size()));

            for(String key: aliasMap.keySet()) {
                sendFeedback(Formatting.BOLD + key + Formatting.RESET+ ": "+ aliasMap.get(key).replace("%","%%"));
            }

        }
        return 0;
    }
    private static int removeAlias(String key) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        var dispatcher = (CommandDispatcher<ServerCommandSource>) (CommandDispatcher<?>) networkHandler.getCommandDispatcher();

        HashMap<String, String> aliasMap = getAliases();

        if(aliasMap != null && aliasMap.containsKey(key)) {
            BrigadierRemover.of(dispatcher).get(key).remove();
            aliasMap.remove(key);
        } else {
            sendError(new LiteralText("No such key exists"));
            return 0;
        }

        try {
            Writer writer = new FileWriter(String.valueOf(aliasPath));
            Gson gson = new Gson();

            gson.toJson(aliasMap, writer);
            writer.flush();
            writer.close();
            sendFeedback(new LiteralText("Alias removed successfully!"));
        } catch (Exception e) {
            sendError(new LiteralText("Uh oh"));
        }

        return 0;
    }

    private static HashMap<String, String> getAliases() {

        LOGGER.info(aliasPath.toString());

        Gson gson = new Gson();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(String.valueOf(aliasPath));
        } catch (Exception e) {
            sendError(new LiteralText("Failed to read alias file."));
            return null;
        }

        return gson.fromJson(new JsonReader(fileReader), HashMap.class);
    }
}
