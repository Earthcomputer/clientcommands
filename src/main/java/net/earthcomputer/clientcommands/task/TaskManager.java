package net.earthcomputer.clientcommands.task;

import java.util.*;

public class TaskManager {

    private static Map<String, LongTask> tasks = new LinkedHashMap<>();
    private static long nextTaskId = 1;

    public static void tick() {
        if (tasks.isEmpty())
            return;

        List<Map.Entry<String, LongTask>> iteratingTasks = new ArrayList<>(tasks.entrySet());
        while (!iteratingTasks.isEmpty()) {
            Iterator<Map.Entry<String, LongTask>> itr = iteratingTasks.iterator();
            while (itr.hasNext()) {
                Map.Entry<String, LongTask> taskEntry = itr.next();
                LongTask task = taskEntry.getValue();
                if (task.isCompleted()) {
                    task.onCompleted();
                    tasks.remove(taskEntry.getKey());
                    itr.remove();
                } else {
                    task.body();
                    if (!task.isCompleted())
                        task.increment();
                    if (task.isDelayScheduled()) {
                        task.unscheduleDelay();
                        itr.remove();
                    }
                }
            }
        }
    }

    public static void onWorldUnload() {
        for (LongTask task : tasks.values()) {
            if (task.stopOnWorldUnload())
                task._break();
        }
    }

    public static String addTask(String name, LongTask task) {
        String actualName = (nextTaskId++) + "." + name;
        tasks.put(actualName, task);
        return actualName;
    }

    public static int getTaskCount() {
        return tasks.size();
    }

    public static Iterable<String> getTaskNames() {
        return tasks.keySet();
    }

    public static void removeTask(String name) {
        LongTask task = tasks.get(name);
        if (task != null)
            task._break();
    }

}
