package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.StringTextComponent;
import net.minecraft.text.TextFormat;
import net.minecraft.text.TranslatableTextComponent;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class TaskCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctask");

        dispatcher.register(literal("ctask")
            .then(literal("list")
                .executes(ctx -> listTasks(ctx.getSource())))
            .then(literal("stop-all")
                .executes(ctx -> stopTasks(ctx.getSource(), "")))
            .then(literal("stop")
                .then(argument("pattern", string())
                    .executes(ctx -> stopTasks(ctx.getSource(), getString(ctx, "pattern"))))));
    }

    private static int listTasks(ServerCommandSource source) {
        Iterable<String> tasks = TaskManager.getTaskNames();
        int taskCount = TaskManager.getTaskCount();

        if (taskCount == 0) {
            sendError(new TranslatableTextComponent("commands.ctask.list.noTasks"));
        } else {
            sendFeedback(new TranslatableTextComponent("commands.ctask.list.success", taskCount).applyFormat(TextFormat.BOLD));
            for (String task : tasks) {
                sendFeedback(new StringTextComponent("- " + task));
            }
        }

        return taskCount;
    }

    private static int stopTasks(ServerCommandSource source, String pattern) {
        List<String> tasksToStop = new ArrayList<>();
        for (String task : TaskManager.getTaskNames()) {
            if (task.contains(pattern))
                tasksToStop.add(task);
        }
        for (String task : tasksToStop)
            TaskManager.removeTask(task);

        if (tasksToStop.isEmpty())
            if (pattern.isEmpty())
                sendError(new TranslatableTextComponent("commands.ctask.list.noTasks"));
            else
                sendError(new TranslatableTextComponent("commands.ctask.stop.noMatch"));
        else
            sendFeedback(new TranslatableTextComponent("commands.ctask.stop.success", tasksToStop.size()));
        return tasksToStop.size();
    }

}
