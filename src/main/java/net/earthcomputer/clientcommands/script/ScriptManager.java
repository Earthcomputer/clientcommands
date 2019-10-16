package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ScriptManager {

    private static final Logger LOGGER = LogManager.getLogger("ScriptManager");
    private static final DynamicCommandExceptionType SCRIPT_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cscript.notFound", arg));

    private static File scriptDir;
    private static Map<String, String> scripts = new HashMap<>();

    static final ScriptEngine ENGINE;
    static {
        ScriptEngine engine;
        try {
            Class<?> factoryCls = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
            Class<?> classFilterCls = Class.forName("jdk.nashorn.api.scripting.ClassFilter");
            Object factory = factoryCls.newInstance();
            Method getScriptEngineMethod = factoryCls.getMethod("getScriptEngine", classFilterCls);
            engine = (ScriptEngine) getScriptEngineMethod.invoke(factory, new ScriptClassFilter());
        } catch (ReflectiveOperationException e) {
            engine = null;
        }
        ENGINE = engine;
    }

    public static void reloadScripts() {
        LOGGER.info("Reloading clientcommands scripts");
        if (ENGINE == null) {
            LOGGER.warn("It appears your Java installation does not include Nashorn. Commonly used JREs should have this, but install a JDK to make sure.");
            return;
        }

        scriptDir = new File(ClientCommands.configDir, "scripts");
        //noinspection ResultOfMethodCallIgnored
        scriptDir.mkdirs();
        Path scriptDirPath = scriptDir.toPath();

        scripts.clear();

        try {
            Files.walk(scriptDirPath, FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            scripts.put(scriptDirPath.relativize(path).toString(), FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("Error reloading clientcommands scripts", e);
        }
    }

    public static Set<String> getScriptNames() {
        return Collections.unmodifiableSet(scripts.keySet());
    }

    public static void execute(String scriptName) throws CommandSyntaxException {
        String scriptSource = scripts.get(scriptName);
        if (scriptSource == null)
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptName);

        ENGINE.put("player", new ScriptPlayer(MinecraftClient.getInstance().player));
        ENGINE.put("world", new ScriptWorld(MinecraftClient.getInstance().world));
        ENGINE.put("$", (Function<String, Integer>) command -> {
            StringReader reader = new StringReader(command);
            String commandName = reader.readUnquotedString();
            reader.setCursor(0);
            if (!ClientCommandManager.isClientSideCommand(commandName)) {
                ClientCommandManager.sendError(new TranslatableText("commands.client.notClient"));
                return 1;
            }
            return ClientCommandManager.executeCommand(reader, command);
        });
        try {
            ENGINE.eval(scriptSource);
        } catch (ScriptException e) {
            ClientCommandManager.sendError(new LiteralText(e.getMessage()));
        }
    }

}
