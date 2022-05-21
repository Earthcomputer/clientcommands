package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.*;

public class VarCommand {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("[%]([^%]+)[%]");

    private static final Logger LOGGER = LogManager.getLogger("clientcommands");

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cvar.saveFile.failed"));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cvar.add.alreadyExists", arg));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cvar.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, String> variables = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load vars file, hence /cvar will not work!");
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cvar")
                .then(literal("add")
                        .then(argument("variable", word())
                                .then(argument("value", greedyString())
                                        .executes(ctx -> addVariable(ctx.getSource(), getString(ctx, "variable"), getString(ctx, "value"))))))
                .then(literal("remove")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggestMatching(variables.keySet(), builder))
                                .executes(ctx -> removeVariable(ctx.getSource(), getString(ctx, "variable")))))
                .then(literal("edit")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggestMatching(variables.keySet(), builder))
                                .then(argument("value", greedyString())
                                        .executes(ctx -> editVariable(ctx.getSource(), getString(ctx, "variable"), getString(ctx, "value"))))))
                .then(literal("parse")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggestMatching(variables.keySet(), builder))
                                .executes(ctx -> parseVariable(ctx.getSource(), getString(ctx, "variable")))))
                .then(literal("list")
                        .executes(ctx -> listVariables(ctx.getSource()))));
    }

    private static int addVariable(FabricClientCommandSource source, String variable, String value) throws CommandSyntaxException {
        if (variables.containsKey(variable)) {
            throw ALREADY_EXISTS_EXCEPTION.create(variable);
        }
        variables.put(variable, value);
        saveFile();
        source.sendFeedback(new TranslatableText("commands.cvar.add.success", variable));
        return 0;
    }

    private static int removeVariable(FabricClientCommandSource source, String variable) throws CommandSyntaxException {
        if (variables.remove(variable) == null) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        saveFile();
        source.sendFeedback(new TranslatableText("commands.cvar.remove.success", variable));
        return 0;
    }

    private static int editVariable(FabricClientCommandSource source, String variable, String value) throws CommandSyntaxException {
        if (!variables.containsKey(variable)) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        variables.put(variable, value);
        saveFile();
        source.sendFeedback(new TranslatableText("commands.cvar.edit.success", variable));
        return 0;
    }

    private static int parseVariable(FabricClientCommandSource source, String variable) throws CommandSyntaxException {
        String value = variables.get(variable);
        if (value == null) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        source.sendFeedback(new TranslatableText("commands.cvar.parse.success", variable, value));
        return 0;
    }

    private static int listVariables(FabricClientCommandSource source) {
        if (variables.isEmpty()) {
            source.sendFeedback(new TranslatableText("commands.cvar.list.empty"));
        } else {
            String list = "%" + String.join("%, %", variables.keySet()) + "%";
            source.sendFeedback(new TranslatableText("commands.cvar.list", list));
        }
        return variables.size();
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            variables.forEach(rootTag::putString);
            File newFile = File.createTempFile("vars", ".dat", configPath.toFile());
            NbtIo.write(rootTag, newFile);
            File backupFile = new File(configPath.toFile(), "vars.dat_old");
            File currentFile = new File(configPath.toFile(), "vars.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        variables.clear();
        NbtCompound rootTag = NbtIo.read(new File(configPath.toFile(), "vars.dat"));
        if (rootTag == null) {
            return;
        }
        rootTag.getKeys().forEach(key -> variables.put(key, rootTag.getString(key)));
    }

    public static String replaceVariables(String originalString) {
        Matcher matcher = VARIABLE_PATTERN.matcher(originalString);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(builder, variables.getOrDefault(group.substring(1, group.length() - 1), group));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
