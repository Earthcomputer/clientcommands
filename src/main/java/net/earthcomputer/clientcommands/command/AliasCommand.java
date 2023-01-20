package net.earthcomputer.clientcommands.command;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.features.BrigadierRemover;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class AliasCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Path ALIAS_PATH = FabricLoader.getInstance().getConfigDir().resolve("clientcommands").resolve("alias_list.json");

    private static final SimpleCommandExceptionType ILLEGAL_FORMAT_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.calias.illegalFormatException"));

    private static final DynamicCommandExceptionType ALIAS_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.calias.addAlias.aliasAlreadyExists", arg));
    private static final DynamicCommandExceptionType COMMAND_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.calias.addAlias.commandAlreadyExists", arg));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.calias.notFound", arg));

    private static final HashMap<String, String> aliasMap = loadAliases();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        try {
            Class.forName("net.fabricmc.fabric.impl.command.client.ClientCommandInternals");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Could not find ClientCommandInternals, /calias command not available");
            return;
        }

        dispatcher.register(literal("calias")
                .then(literal("add")
                        .then(argument("key", string())
                                .then(argument("command", greedyString())
                                        .executes(ctx -> addAlias(ctx.getSource(), getString(ctx, "key"), getString(ctx, "command"))))))
                .then(literal("list")
                        .executes(ctx -> listAliases(ctx.getSource())))
                .then(literal("remove")
                        .then(argument("key", string())
                                .executes(ctx -> removeAlias(ctx.getSource(), getString(ctx, "key"))))));

        for (String key : aliasMap.keySet()) {
            if (dispatcher.getRoot().getChildren().stream().map(CommandNode::getName).noneMatch(literal -> literal.equals(key))) {
                dispatcher.register(literal(key)
                        .executes(ctx -> executeAliasCommand(ctx.getSource(), key, null))
                        .then(argument("arguments", greedyString())
                                .executes(ctx -> executeAliasCommand(ctx.getSource(), key, getString(ctx, "arguments")))));
            } else {
                LOGGER.error("Attempted to register alias /{}, but that command already exists", key);
            }
        }
    }

    private static int executeAliasCommand(FabricClientCommandSource source, String aliasKey, String arguments) throws CommandSyntaxException {
        String cmd = aliasMap.get(aliasKey);
        if (cmd == null) {
            throw NOT_FOUND_EXCEPTION.create(aliasKey);
        }
        int inlineArgumentCount = (int) Pattern.compile("(?<!%)%(?:%%)*(?!%)").matcher(cmd).results().count();
        if (inlineArgumentCount > 0) {
            String[] argumentArray = arguments.split(" ", inlineArgumentCount + 1);

            String trailingArguments = "";
            if (argumentArray.length > inlineArgumentCount) {
                trailingArguments = " " + argumentArray[inlineArgumentCount];
            }
            try {
                cmd = String.format(cmd, (Object[]) argumentArray) + trailingArguments;
            } catch (IllegalFormatException e) {
                throw ILLEGAL_FORMAT_EXCEPTION.create();
            }
        } else if (arguments != null){
            cmd += " " + arguments;
        }
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
            networkHandler.sendChatCommand(cmd);
        } else {
            networkHandler.sendChatMessage(cmd);
        }

        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static int addAlias(FabricClientCommandSource source, String key, String command) throws CommandSyntaxException {
        if (aliasMap.containsKey(key)) {
            throw ALIAS_EXISTS_EXCEPTION.create(key);
        }
        if (ClientCommandManager.getActiveDispatcher().getRoot().getChildren().stream().map(CommandNode::getName).anyMatch(literal -> literal.equals(key))) {
            throw COMMAND_EXISTS_EXCEPTION.create(key);
        }
        if (!command.startsWith("/")) {
            command = "/" + command;
        }

        for (CommandDispatcher<FabricClientCommandSource> dispatcher : new CommandDispatcher[] { ClientCommandManager.getActiveDispatcher(), MinecraftClient.getInstance().getNetworkHandler().getCommandDispatcher() }) {
            dispatcher.register(literal(key)
                    .executes(ctx -> executeAliasCommand(source, key, null))
                    .then(argument("arguments", greedyString())
                            .executes(ctx -> executeAliasCommand(source, key, getString(ctx, "arguments")))));
        }

        aliasMap.put(key, command);

        saveAliases();
        source.sendFeedback(Text.translatable("commands.calias.addAlias.success", key));
        return Command.SINGLE_SUCCESS;
    }

    private static int listAliases(FabricClientCommandSource source) {
        if (aliasMap.isEmpty()) {
            source.sendFeedback(Text.translatable("commands.calias.listAliases.noAliasesRegistered"));
        } else {
            source.sendFeedback(Text.translatable("commands.calias.listAliases.success", aliasMap.size()));
            for (String key : aliasMap.keySet()) {
                source.sendFeedback(Text.of(Formatting.BOLD + key + Formatting.RESET + ": " + aliasMap.get(key).replace("%","%%")));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeAlias(FabricClientCommandSource source, String key) throws CommandSyntaxException {
        if (aliasMap.containsKey(key)) {
            BrigadierRemover.of(ClientCommandManager.getActiveDispatcher()).get(key).remove();
            BrigadierRemover.of(MinecraftClient.getInstance().getNetworkHandler().getCommandDispatcher()).get(key).remove();
            aliasMap.remove(key);
        } else {
            throw NOT_FOUND_EXCEPTION.create(key);
        }

        saveAliases();
        source.sendFeedback(Text.translatable("commands.calias.removeAlias.success", key));
        return Command.SINGLE_SUCCESS;
    }

    private static HashMap<String, String> loadAliases() {
        if (!Files.exists(ALIAS_PATH)) {
            return new HashMap<>();
        }
        Gson gson = new Gson();
        try (Reader fileReader = Files.newBufferedReader(ALIAS_PATH)) {
            return gson.fromJson(new JsonReader(fileReader), new TypeToken<HashMap<String, String>>(){}.getType());
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Error reading aliases file", e);
            return new HashMap<>();
        }
    }

    private static void saveAliases() {
        try (Writer writer = Files.newBufferedWriter(ALIAS_PATH)) {
            Gson gson = new Gson();
            gson.toJson(aliasMap, writer);
            writer.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to save aliases", e);
        }
    }
}
