package net.earthcomputer.clientcommands.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import net.earthcomputer.clientcommands.EventManager;
import net.minecraft.command.CommandException;

public class TaskManager {

	private TaskManager() {
	}

	private static List<GuiBlocker> guiBlockers = new ArrayList<>();
	private static Queue<LongTask> longTaskQueue = new ArrayDeque<>();
	private static LongTask currentLongTask;

	public static void addGuiBlocker(GuiBlocker blocker) {
		guiBlockers.add(blocker);
	}

	public static void ensureNoTasks() throws CommandException {
		if (currentLongTask != null || !longTaskQueue.isEmpty()) {
			throw new CommandException("Looks like there is already a task running! Try /cabort.");
		}
	}

	public static boolean abortTasks() {
		boolean result = !longTaskQueue.isEmpty() || currentLongTask != null;
		longTaskQueue.clear();
		if (currentLongTask != null) {
			currentLongTask.cleanup();
			currentLongTask = null;
		}
		return result;
	}

	public static void addLongTask(LongTask task) {
		longTaskQueue.add(task);
	}

	static {
		EventManager.addDisconnectListener(e -> {
			abortTasks();
			guiBlockers.clear();
		});

		EventManager.addGuiOpenListener(e -> {
			Iterator<GuiBlocker> guiBlockerItr = guiBlockers.iterator();
			while (guiBlockerItr.hasNext()) {
				GuiBlocker guiBlocker = guiBlockerItr.next();
				if (guiBlocker.isFinished()) {
					guiBlockerItr.remove();
				} else {
					if (!guiBlocker.processGui(e.getGui())) {
						e.setCanceled(true);
					}
					if (guiBlocker.isFinished()) {
						guiBlockerItr.remove();
					}
				}
			}
		});

		EventManager.addTickListener(e -> {
			while (true) {
				if (currentLongTask == null) {
					if (!longTaskQueue.isEmpty()) {
						currentLongTask = longTaskQueue.remove();
						currentLongTask.start();
					} else {
						break;
					}
				}
				if (currentLongTask.isFinished()) {
					currentLongTask.cleanup();
					currentLongTask = null;
				} else {
					currentLongTask.tick();
					if (currentLongTask != null && currentLongTask.isFinished()) {
						currentLongTask.cleanup();
						currentLongTask = null;
					} else {
						break;
					}
				}
			}
		});
	}

}
