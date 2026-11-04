package net.earthcomputer.clientcommands.server;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public final class ServerTaskQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_TICK_TIME = 5_000_000L; // 5ms

    private static final Deque<ServerTask> tasks = new ArrayDeque<>();

    private ServerTaskQueue() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTaskQueue::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> cancelIf(_ -> true));
    }

    public static void add(ServerTask task) {
        tasks.add(task);
    }

    public static int getTaskCount() {
        return tasks.size();
    }

    public static void cancelIf(Predicate<? super ServerTask> predicate) {
        List<ServerTask> cancelled = new ArrayList<>();
        tasks.removeIf(task -> {
            if (predicate.test(task)) {
                cancelled.add(task);
                return true;
            }
            return false;
        });
        cancelled.forEach(ServerTask::onCancelled);
    }

    private static void tick(MinecraftServer server) {
        if (tasks.isEmpty()) {
            return;
        }

        long deadline = System.nanoTime() + MAX_TICK_TIME;
        List<ServerTask> yieldedTasks = new ArrayList<>();
        while (!tasks.isEmpty()) {
            ServerTask task = tasks.element();
            ServerTask.Status status;
            try {
                status = task.tick(server, deadline);
            } catch (Throwable e) {
                LOGGER.error("Error ticking server task {}", task, e);
                tasks.remove();
                task.onCancelled();
                continue;
            }

            if (status == ServerTask.Status.IN_PROGRESS) {
                break;
            }
            tasks.remove();
            if (status == ServerTask.Status.YIELD) {
                yieldedTasks.add(task);
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        tasks.addAll(yieldedTasks);
    }
}
