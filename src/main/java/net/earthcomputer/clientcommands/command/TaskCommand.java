package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TaskCommand {

    private static final SimpleCommandExceptionType NO_MATCH_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ctask.stop.noMatch"));

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
            source.sendFeedback(Component.translatable("commands.ctask.list.noTasks"));
        } else {
            source.sendFeedback(Component.translatable("commands.ctask.list.success", taskCount).withStyle(ChatFormatting.BOLD));
            for (String task : tasks) {
                source.sendFeedback(Component.literal("- " + task));
            }
        }

        return taskCount;
    }

    private static int stopTasks(FabricClientCommandSource source, String pattern) throws CommandSyntaxException {
        List<String> tasksToStop = new ArrayList<>();
        for (String task : TaskManager.getTaskNames()) {
            if (task.contains(pattern)) {
                tasksToStop.add(task);
            }
        }
        for (String task : tasksToStop) {
            TaskManager.removeTask(task);
        }

        if (tasksToStop.isEmpty()) {
            if (pattern.isEmpty()) {
                source.sendFeedback(Component.translatable("commands.ctask.list.noTasks"));
            } else {
                throw NO_MATCH_EXCEPTION.create();
            }
        } else {
            source.sendFeedback(Component.translatable("commands.ctask.stop.success", tasksToStop.size()));
        }
        return tasksToStop.size();
    }

}
