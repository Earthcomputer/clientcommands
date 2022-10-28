package net.earthcomputer.clientcommands.features;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.VarCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.FileNameUtil;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

public class ClientCommandFunctions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Path FUNCTION_DIR = ClientCommands.configDir.resolve("functions");

    private static final DynamicCommandExceptionType NO_SUCH_FUNCTION_EXCEPTION = new DynamicCommandExceptionType(id -> Text.translatable("arguments.function.unknown", id));
    private static final DynamicCommandExceptionType COMMAND_LIMIT_REACHED_EXCEPTION = new DynamicCommandExceptionType(limit -> Text.translatable("commands.cfunction.limitReached", limit));

    @Nullable
    public static Path getLocalStartupFunction() {
        return CommandFunction.getPath(getLocalStartupFunctionStr());
    }

    private static String getLocalStartupFunctionStr() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo mpServer = mc.getCurrentServerEntry();
        String startupFunction;
        if (mpServer != null) {
            startupFunction = "startup_multiplayer_" + mpServer.address.replace(':', '_');
        } else {
            IntegratedServer server = mc.getServer();
            if (server != null) {
                startupFunction = "startup_singleplayer_" + server.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
            } else {
                startupFunction = "startup";
            }
        }
        return startupFunction;
    }

    public static boolean ensureLocalStartupFunctionExists() throws IOException {
        Path file = getLocalStartupFunction();
        if (file == null) {
            return false;
        }
        if (Files.exists(file)) {
            return true;
        }

        ensureGlobalStartupFunctionExists();

        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.append("# This file contains commands to be run when joining this world/server.");
            writer.newLine();
            writer.newLine();
            writer.append("# Run the default startup commands.");
            writer.newLine();
            writer.append("cfunction startup");
            writer.newLine();
        }
        return true;
    }

    public static Path getGlobalStartupFunction() {
        return FUNCTION_DIR.resolve("startup.mcfunction");
    }

    public static void ensureGlobalStartupFunctionExists() throws IOException {
        Path file = getGlobalStartupFunction();
        if (Files.exists(file)) {
            return;
        }

        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.append("# This file contains commands to be run when joining any world/server.");
            writer.newLine();
        }
    }

    public static void runStartup() {
        String startupFunction = getLocalStartupFunctionStr();
        Path path = CommandFunction.getPath(startupFunction);
        if (path == null || !Files.exists(path)) {
            startupFunction = "startup";
            path = CommandFunction.getPath(startupFunction);
            if (path == null || !Files.exists(path)) {
                return;
            }
        }

        LOGGER.info("Running startup function {}", startupFunction);

        try {
            var dispatcher = ClientCommandManager.getActiveDispatcher();
            var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
            assert networkHandler != null : "Network handler should not be null while calling ClientCommandFunctions.runStartup()";
            var source = (FabricClientCommandSource) networkHandler.getCommandSource();
            int result = executeFunction(dispatcher, source, startupFunction, res -> {});
            LOGGER.info("Ran {} commands from startup function {}", result, startupFunction);
        } catch (CommandSyntaxException e) {
            LOGGER.error("Error running startup function {}: {}", startupFunction, e.getMessage());
        }
    }

    public static List<String> allFunctions() {
        try (Stream<Path> paths = Files.walk(FUNCTION_DIR)) {
            return paths.filter(path -> !Files.isDirectory(path) && path.getFileName().toString().endsWith(".mcfunction")).map(path -> {
                String name = FUNCTION_DIR.relativize(path).toString();
                return name.substring(0, name.length() - ".mcfunction".length()).replace(File.separator, "/");
            }).toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Nullable
    private static ExecutionContext currentContext = null;

    public static int executeFunction(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source, String function, IntConsumer successMessageSender) throws CommandSyntaxException {
        if (currentContext != null) {
            CommandFunction func = currentContext.functions.get(function);
            if (func == null) {
                func = CommandFunction.load(currentContext.dispatcher, currentContext.source, function);
                currentContext.functions.put(function, func);
            }
            for (int i = func.entries.size() - 1; i >= 0; i--) {
                currentContext.entries.addFirst(func.entries.get(i));
            }
            return Command.SINGLE_SUCCESS;
        }

        CommandFunction func = CommandFunction.load(dispatcher, source, function);
        Map<String, CommandFunction> functions = new HashMap<>();
        functions.put(function, func);

        Deque<Entry> entries = new ArrayDeque<>(func.entries);

        currentContext = new ExecutionContext(dispatcher, source, entries, functions);
        try {
            int count = 0;
            while (!entries.isEmpty()) {
                if (count++ >= TempRules.commandExecutionLimit) {
                    throw COMMAND_LIMIT_REACHED_EXCEPTION.create(TempRules.commandExecutionLimit);
                }
                entries.remove().execute(dispatcher, source);
            }
            successMessageSender.accept(count);
            return count;
        } finally {
            currentContext = null;
        }
    }

    private record ExecutionContext(
        CommandDispatcher<FabricClientCommandSource> dispatcher,
        FabricClientCommandSource source,
        Deque<Entry> entries,
        Map<String, CommandFunction> functions
    ) {
    }

    private record CommandFunction(ImmutableList<Entry> entries) {
        @Nullable
        static Path getPath(String function) {
            if (!"/".equals(File.separator)) {
                if (function.contains(File.separator)) {
                    return null;
                }
                function = function.replace(File.separator, "/");
            }
            Path path;
            try {
                path = FUNCTION_DIR.resolve(function + ".mcfunction");
            } catch (InvalidPathException e) {
                return null;
            }
            if (!FileNameUtil.isNormal(path) || !FileNameUtil.isAllowedName(path)) {
                return null;
            }
            return path;
        }

        static CommandFunction load(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source, String function) throws CommandSyntaxException {
            Path path = getPath(function);
            if (path == null || !Files.exists(path)) {
                throw NO_SUCH_FUNCTION_EXCEPTION.create(function);
            }

            var entries = ImmutableList.<Entry>builder();
            try (Stream<String> lines = Files.lines(path)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (VarCommand.containsVars(line)) {
                        entries.add(new LazyEntry(line));
                    } else {
                        var command = dispatcher.parse(line, source);
                        if (command.getReader().canRead()) {
                            //noinspection ConstantConditions
                            throw CommandManager.getException(command);
                        }
                        entries.add(new ParsedEntry(command));
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read function file {}", path, e);
                throw NO_SUCH_FUNCTION_EXCEPTION.create(function);
            }

            return new CommandFunction(entries.build());
        }
    }

    private interface Entry {
        void execute(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source) throws CommandSyntaxException;
    }

    private record ParsedEntry(ParseResults<FabricClientCommandSource> command) implements Entry {
        @Override
        public void execute(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source) throws CommandSyntaxException {
            dispatcher.execute(command);
        }
    }

    private record LazyEntry(String command) implements Entry {
        @Override
        public void execute(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source) throws CommandSyntaxException {
            dispatcher.execute(VarCommand.replaceVariables(command), source);
        }
    }
}
