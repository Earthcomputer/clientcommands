package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.earthcomputer.clientcommands.command.FakeCommandSource;
import net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScriptManager {

    private static final Logger LOGGER = LogManager.getLogger("ScriptManager");
    private static final DynamicCommandExceptionType SCRIPT_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cscript.notFound", arg));

    private static File scriptDir;
    private static Map<String, String> scripts = new HashMap<>();

    private static ScriptInstance currentScript = null;
    private static List<ScriptInstance> runningScripts = new ArrayList<>();

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
        addBuiltinVariables();
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

        ScriptInstance instance = new ScriptInstance();
        Thread thread = new Thread(() -> {
            try {
                ENGINE.eval(scriptSource);
            } catch (ScriptInterruptedException ignore) {
            } catch (ScriptException e) {
                if (!(e.getCause() instanceof ScriptInterruptedException)) {
                    ClientCommandManager.sendError(new LiteralText(e.getMessage()));
                    e.getCause().printStackTrace();
                }
            } catch (Throwable e) {
                ClientCommandManager.sendError(new LiteralText(e.toString()));
                e.printStackTrace();
            }
            instance.paused.set(true);
            instance.running = false;
            runningScripts.remove(instance);
        });
        thread.setDaemon(true);
        instance.thread = thread;
        LongTask task = new LongTask() {
            @Override
            public void initialize() {
            }

            @Override
            public boolean condition() {
                return instance.running;
            }

            @Override
            public void increment() {
            }

            @Override
            public void body() {
                scheduleDelay();
            }
        };
        instance.task = task;
        TaskManager.addTask("cscript", task);
        runningScripts.add(instance);
        currentScript = instance;

        thread.start();
        while (!instance.paused.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void addBuiltinVariables() {
        ENGINE.put("player", new ScriptPlayer());
        ENGINE.put("world", new ScriptWorld());
        ENGINE.put("$", (Function<String, Object>) command -> {
            StringReader reader = new StringReader(command);
            if (command.startsWith("@")) {
                try {
                    ClientEntitySelector selector = ClientEntityArgumentType.entities().parse(reader);
                    if (reader.getRemainingLength() != 0)
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(reader);
                    List<Entity> entities = selector.getEntities(new FakeCommandSource(MinecraftClient.getInstance().player));
                    List<Object> ret = new ArrayList<>(entities.size());
                    for (Entity entity : entities)
                        ret.add(ScriptEntity.create(entity));
                    return ret;
                } catch (CommandSyntaxException e) {
                    throw new IllegalArgumentException("Invalid selector syntax", e);
                }
            }
            String commandName = reader.readUnquotedString();
            reader.setCursor(0);
            if (!ClientCommandManager.isClientSideCommand(commandName)) {
                ClientCommandManager.sendError(new TranslatableText("commands.client.notClient"));
                return 1;
            }
            return ClientCommandManager.executeCommand(reader, command);
        });
        ENGINE.put("print", (Consumer<String>) message -> MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(message)));
        ENGINE.put("tick", (Runnable) ScriptManager::passTick);
    }

    public static void tick() {
        if (ENGINE == null)
            return;

        for (ScriptInstance script : new ArrayList<>(runningScripts)) {
            currentScript = script;
            script.paused.set(false);
            while (!script.paused.get()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        currentScript = null;
    }

    static void passTick() {
        ScriptInstance script = currentScript;
        script.paused.set(true);
        while (script.paused.get()) {
            if (script.task.isCompleted())
                throw new ScriptInterruptedException();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void blockInput(boolean blockInput) {
        currentScript.blockingInput = blockInput;
    }

    static boolean isCurrentScriptBlockingInput() {
        return currentScript.blockingInput;
    }

    public static boolean blockingInput() {
        for (ScriptInstance script : runningScripts)
            if (script.blockingInput)
                return true;
        return false;
    }

    static Input getScriptInput() {
        return currentScript.input;
    }

    public static void copyScriptInputToPlayer(boolean inSneakingPose, boolean spectator) {
        Input playerInput = MinecraftClient.getInstance().player.input;
        for (ScriptInstance script : runningScripts) {
            playerInput.pressingForward |= script.input.pressingForward;
            playerInput.pressingBack |= script.input.pressingBack;
            playerInput.pressingLeft |= script.input.pressingLeft;
            playerInput.pressingRight |= script.input.pressingRight;
            playerInput.jumping |= script.input.jumping;
            playerInput.sneaking |= script.input.sneaking;
        }
        playerInput.movementForward = playerInput.pressingForward ^ playerInput.pressingBack ? (playerInput.pressingForward ? 1 : -1) : 0;
        playerInput.movementSideways = playerInput.pressingLeft ^ playerInput.pressingRight ? (playerInput.pressingLeft ? 1 : -1) : 0;
        if (!spectator && (playerInput.sneaking || inSneakingPose)) {
            playerInput.movementSideways = (float)(playerInput.movementSideways * 0.3D);
            playerInput.movementForward = (float)(playerInput.movementForward * 0.3D);
        }
    }

    private static class ScriptInstance {
        private Thread thread;
        private AtomicBoolean paused = new AtomicBoolean(false);
        private boolean running = true;
        private LongTask task;
        private boolean blockingInput = false;
        private Input input = new Input();
    }

}
