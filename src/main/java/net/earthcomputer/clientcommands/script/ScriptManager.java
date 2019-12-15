package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScriptManager {

    private static final Logger LOGGER = LogManager.getLogger("ScriptManager");
    private static final DynamicCommandExceptionType SCRIPT_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cscript.notFound", arg));

    private static File scriptDir;
    private static Map<String, String> scripts = new HashMap<>();

    private static Deque<ThreadInstance> threadStack = new ArrayDeque<>();
    private static List<ThreadInstance> runningThreads = new ArrayList<>();

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
        if (engine != null)
            ScriptBuiltins.addBuiltinVariables(engine);
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

    static ThreadInstance currentThread() {
        return threadStack.peek();
    }

    public static void execute(String scriptName) throws CommandSyntaxException {
        String scriptSource = scripts.get(scriptName);
        if (scriptSource == null)
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptName);

        ThreadInstance thread = createThread(() -> (Void)ENGINE.eval(scriptSource), false);
        runThread(thread);
    }

    static ThreadInstance createThread(Callable<Void> task, boolean daemon) {
        ThreadInstance thread = new ThreadInstance();
        thread.handle = new ScriptThread(thread);
        thread.javaThread = new Thread(() -> {
            try {
                task.call();
            } catch (ScriptInterruptedException ignore) {
            } catch (ScriptException e) {
                if (!(e.getCause() instanceof ScriptInterruptedException)) {
                    ClientCommandManager.sendError(new LiteralText(e.getMessage()));
                    e.getCause().printStackTrace();
                }
            } catch (Throwable e) {
                ClientCommandManager.sendError(new LiteralText(e.getMessage()));
                e.printStackTrace();
            }
            runningThreads.remove(thread);
            if (thread.parent != null)
                thread.parent.children.remove(thread);
            for (ThreadInstance child : thread.children) {
                child.parent = null;
                if (child.daemon)
                    child.killed = true;
            }
            thread.running = false;
            thread.blocked.set(true);
        });
        thread.javaThread.setDaemon(true);
        thread.task = new LongTask() {
            @Override
            public void initialize() {
            }

            @Override
            public boolean condition() {
                return thread.running;
            }

            @Override
            public void increment() {
            }

            @Override
            public void body() {
                scheduleDelay();
            }
        };
        thread.daemon = daemon;

        return thread;
    }

    static void runThread(ThreadInstance thread) {
        TaskManager.addTask("cscript", thread.task);
        runningThreads.add(thread);
        thread.running = true;

        if (currentThread() != null) {
            currentThread().children.add(thread);
            thread.parent = currentThread();
        }

        threadStack.push(thread);
        thread.javaThread.start();
        while (!thread.blocked.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threadStack.pop();
    }

    public static void tick() {
        if (ENGINE == null)
            return;

        for (ThreadInstance thread : new ArrayList<>(runningThreads)) {
            if (thread.paused || !thread.running) continue;

            threadStack.push(thread);
            thread.blocked.set(false);
            while (!thread.blocked.get()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            threadStack.pop();
        }
    }

    static void passTick() {
        ThreadInstance thread = currentThread();
        thread.blocked.set(true);
        while (thread.blocked.get()) {
            if (thread.killed || thread.task.isCompleted())
                throw new ScriptInterruptedException();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void blockInput(boolean blockInput) {
        currentThread().blockingInput = blockInput;
    }

    static boolean isCurrentScriptBlockingInput() {
        return currentThread().blockingInput;
    }

    public static boolean blockingInput() {
        for (ThreadInstance script : runningThreads)
            if (script.blockingInput)
                return true;
        return false;
    }

    static Input getScriptInput() {
        return currentThread().input;
    }

    public static void copyScriptInputToPlayer(boolean inSneakingPose) {
        Input playerInput = MinecraftClient.getInstance().player.input;
        for (ThreadInstance thread : runningThreads) {
            playerInput.pressingForward |= thread.input.pressingForward;
            playerInput.pressingBack |= thread.input.pressingBack;
            playerInput.pressingLeft |= thread.input.pressingLeft;
            playerInput.pressingRight |= thread.input.pressingRight;
            playerInput.jumping |= thread.input.jumping;
            playerInput.sneaking |= thread.input.sneaking;
        }
        playerInput.movementForward = playerInput.pressingForward ^ playerInput.pressingBack ? (playerInput.pressingForward ? 1 : -1) : 0;
        playerInput.movementSideways = playerInput.pressingLeft ^ playerInput.pressingRight ? (playerInput.pressingLeft ? 1 : -1) : 0;
        if (playerInput.sneaking || inSneakingPose) {
            playerInput.movementSideways = (float)(playerInput.movementSideways * 0.3D);
            playerInput.movementForward = (float)(playerInput.movementForward * 0.3D);
        }
    }

    static void setSprinting(boolean sprinting) {
        currentThread().sprinting = sprinting;
    }

    static boolean isCurrentThreadSprinting() {
        return currentThread().sprinting;
    }

    public static boolean isSprinting() {
        for (ThreadInstance thread : runningThreads)
            if (thread.sprinting)
                return true;
        return false;
    }

    static class ThreadInstance {
        ScriptThread handle;

        boolean daemon;
        boolean paused;
        boolean killed;
        ThreadInstance parent;
        List<ThreadInstance> children = new ArrayList<>(0);

        private Thread javaThread;
        private AtomicBoolean blocked = new AtomicBoolean(false);
        boolean running;
        private LongTask task;
        private boolean blockingInput = false;
        private Input input = new Input();
        private boolean sprinting = false;
    }

}
