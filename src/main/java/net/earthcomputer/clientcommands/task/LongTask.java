package net.earthcomputer.clientcommands.task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.earthcomputer.clientcommands.ClientCommandsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public abstract class LongTask {

	protected static final Logger LOGGER = LogManager.getLogger(ClientCommandsMod.MODID);
	private int ticks;
	private boolean finished = false;

	public void start() {
	}

	public void cleanup() {
	}

	public final void tick() {
		ticks++;
		if (ticks > getTimeout()) {
			Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
					new TextComponentString(TextFormatting.RED + "LongTask timed out, aborting all tasks"));
			TaskManager.abortTasks();
		} else {
			taskTick();
		}
	}

	protected abstract void taskTick();

	protected int getTimeout() {
		return 100;
	}

	protected final void setFinished() {
		finished = true;
	}

	public final boolean isFinished() {
		return finished;
	}

}
