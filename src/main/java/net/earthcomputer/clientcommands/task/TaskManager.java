package net.earthcomputer.clientcommands.task;

import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.features.Relogger;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TaskManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, LongTask> tasks = new LinkedHashMap<>();
    private static long nextTaskId = 1;
    private static String forceAddedTaskName = null;

    public static void tick() {
        if (tasks.isEmpty()) {
            return;
        }

        Set<Object> mutexKeys = new HashSet<>();

        int iterationCount = 0;
        var iteratingTasks = new ArrayList<>(tasks.entrySet());
        while (!iteratingTasks.isEmpty()) {
            var itr = iteratingTasks.iterator();
            boolean tickedAnyTask = false;
            while (itr.hasNext()) {
                var taskEntry = itr.next();
                LongTask task = taskEntry.getValue();
                Set<Object> taskMutexKeys = task.getMutexKeys();
                if (mutexKeys.stream().anyMatch(taskMutexKeys::contains)) {
                    continue;
                }
                tickedAnyTask = true;
                mutexKeys.addAll(taskMutexKeys);
                if (!task.isInitialized) {
                    task.initialize();
                    task.isInitialized = true;
                }
                if (task.isCompleted()) {
                    forceAddedTaskName = null;
                    task.onCompleted();
                    if (!taskEntry.getKey().equals(forceAddedTaskName)) {
                        tasks.remove(taskEntry.getKey());
                    }
                    itr.remove();
                    mutexKeys.removeAll(taskMutexKeys);
                } else {
                    task.body();
                    if (!task.isCompleted()) {
                        task.increment();
                    }
                    if (task.isDelayScheduled()) {
                        task.unscheduleDelay();
                        itr.remove();
                    }
                }
            }

            if (!tickedAnyTask) {
                break;
            }

            if (FabricLoader.getInstance().isDevelopmentEnvironment() && ++iterationCount == 1000) {
                LOGGER.warn("A LongTask is taking an exceptionally long time. Task list: {}", tasks);
                LOGGER.warn("Remember you can use ctrl+F2 in IntelliJ to terminate the game while it has your cursor grabbed.");
            }
        }
    }

    public static void onLevelUnload(boolean isDisconnect) {
        var oldTasks = new ArrayList<Map.Entry<String, LongTask>>();
        {
            var itr = tasks.entrySet().iterator();
            while (itr.hasNext()) {
                var entry = itr.next();
                if (entry.getValue().stopOnLevelUnload(isDisconnect)) {
                    itr.remove();
                    oldTasks.add(entry);
                }
            }
        }

        if (isDisconnect && Relogger.isRelogging) {
            Relogger.relogSuccessTasks.add(() -> {
                for (var oldTask : oldTasks) {
                    tasks.put(oldTask.getKey(), oldTask.getValue());
                }
            });
        } else {
            for (var taskEntry : oldTasks) {
                taskEntry.getValue().onCompleted();
            }
        }
    }

    public static String addTask(String name, LongTask task) {
        String actualName = (nextTaskId++) + "." + name;
        tasks.put(actualName, task);
        return actualName;
    }

    public static void forceAddTask(String fullName, LongTask task) {
        tasks.put(fullName, task);
        forceAddedTaskName = fullName;
    }

    public static int getTaskCount() {
        return tasks.size();
    }

    public static Iterable<String> getTaskNames() {
        return tasks.keySet();
    }

    public static void removeTask(String name) {
        LongTask task = tasks.get(name);
        if (task != null) {
            task._break();
        }
    }

}
