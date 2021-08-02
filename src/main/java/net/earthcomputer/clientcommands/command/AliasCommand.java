package net.earthcomputer.clientcommands.command;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.BrigadierRemover;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class AliasCommand {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Path aliasPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands").resolve("alias_list.json");

    private static final SimpleCommandExceptionType FILE_READ_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.calias.file.readError"));
    private static final SimpleCommandExceptionType FILE_WRITE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.calias.file.writeError"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.calias.addAlias.alreadyExists", arg));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.calias.notFound", arg));

    private static HashMap<String, String> aliasMap = new HashMap<>();

    static {
        try {
            getAliases();
        } catch (Exception e) {
            LOGGER.info("No alias file provided. A new one will be created upon registering an alias.");
        }
    }

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

        if (aliasMap != null && !aliasMap.isEmpty()) {
            for(String key: aliasMap.keySet()) {
                addClientSideCommand(key);
                dispatcher.register(literal(key)
                        .executes(ctx -> executeAliasCommand(key, null))
                        .then(argument("arguments", greedyString())
                                .executes(ctx -> executeAliasCommand(key, getString(ctx, "arguments")))));
            }
        }

    }
    private static int executeAliasCommand(String aliasKey, String arguments) throws CommandSyntaxException {

        // TODO: add support for optional greedy text arguments(?)
        String cmd;
        if(aliasMap != null && aliasMap.containsKey(aliasKey)) {
            cmd = aliasMap.get(aliasKey);
        } else {
            throw NOT_FOUND_EXCEPTION.create(aliasKey);
        }

        if(arguments!=null) {
            cmd = String.format(cmd, arguments.split(" "));
        }
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendChatMessage(cmd);

        return 0;
    }

    private static int addAlias(String key, String command) throws CommandSyntaxException {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        var dispatcher = (CommandDispatcher<ServerCommandSource>) (CommandDispatcher<?>) networkHandler.getCommandDispatcher();

        assert aliasMap != null;
        if(aliasMap.containsKey(key)) {
            throw ALREADY_EXISTS_EXCEPTION.create(key);
        }
        addClientSideCommand(key);

        dispatcher.register(literal(key)
                .executes(ctx -> executeAliasCommand(key, null))
                .then(argument("arguments", greedyString())
                        .executes(ctx -> executeAliasCommand(key, getString(ctx, "arguments")))));
        aliasMap.put(key, command);

        saveAliases(new TranslatableText("commands.calias.addAlias.success", key));
        return 0;
    }
    private static int listAliases() {
        if (aliasMap.isEmpty()) {
            sendFeedback(new TranslatableText("commands.calias.listAliases.noAliasesRegistered"));
        } else {
            sendFeedback("commands.calias.listAliases.success", aliasMap.size());

            for(String key: aliasMap.keySet()) {
                sendFeedback(Formatting.BOLD + key + Formatting.RESET+ ": "+ aliasMap.get(key).replace("%","%%"));
            }
        }
        return 0;
    }
    private static int removeAlias(String key) throws CommandSyntaxException {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        var dispatcher = (CommandDispatcher<ServerCommandSource>) (CommandDispatcher<?>) networkHandler.getCommandDispatcher();

        if(aliasMap != null && aliasMap.containsKey(key)) {
            BrigadierRemover.of(dispatcher).get(key).remove();
            aliasMap.remove(key);
        } else {
            throw NOT_FOUND_EXCEPTION.create(key);
        }

        saveAliases(new TranslatableText("commands.calias.removeAlias.success", key));
        return 0;
    }

    private static void getAliases() throws CommandSyntaxException {

        LOGGER.info("Alias config path:" + aliasPath.toString());

        Gson gson = new Gson();
        try (FileReader fileReader = new FileReader(String.valueOf(aliasPath))){
            aliasMap = gson.fromJson(new JsonReader(fileReader), HashMap.class);
        } catch (Exception e) {
            throw FILE_READ_EXCEPTION.create();
        }
    }

    private static void saveAliases(TranslatableText successMessage) throws CommandSyntaxException {
        try (Writer writer = new FileWriter(String.valueOf(aliasPath))) {
            Gson gson = new Gson();
            gson.toJson(aliasMap, writer);

            sendFeedback(successMessage);
        } catch (Exception e) {
            throw FILE_WRITE_EXCEPTION.create();
        }
    }
}
