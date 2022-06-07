package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TaskCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctask")
            .then(literal("list")
                .executes(ctx -> listTasks(ctx.getSource())))
            .then(literal("stop-all")
                .executes(ctx -> stopTasks(ctx.getSource(), "")))
            .then(literal("stop")
                .then(argument("pattern", string())
                    .executes(ctx -> stopTasks(ctx.getSource(), getString(ctx, "pattern"))))));
    }

    private static int listTasks(FabricClientCommandSource source) {
        Iterable<String> tasks = TaskManager.getTaskNames();
        int taskCount = TaskManager.getTaskCount();

        if (taskCount == 0) {
            source.sendError(Text.translatable("commands.ctask.list.noTasks"));
        } else {
            source.sendFeedback(Text.translatable("commands.ctask.list.success", taskCount).formatted(Formatting.BOLD));
            for (String task : tasks) {
                source.sendFeedback(Text.literal("- " + task));
            }
        }

        return taskCount;
    }

    private static int stopTasks(FabricClientCommandSource source, String pattern) {
        List<String> tasksToStop = new ArrayList<>();
        for (String task : TaskManager.getTaskNames()) {
            if (task.contains(pattern))
                tasksToStop.add(task);
        }
        for (String task : tasksToStop)
            TaskManager.removeTask(task);

        if (tasksToStop.isEmpty()) {
            if (pattern.isEmpty()) {
                source.sendError(Text.translatable("commands.ctask.list.noTasks"));
            } else {
                source.sendError(Text.translatable("commands.ctask.stop.noMatch"));
            }
        } else {
            source.sendFeedback(Text.translatable("commands.ctask.stop.success", tasksToStop.size()));
        }
        return tasksToStop.size();
    }

}
