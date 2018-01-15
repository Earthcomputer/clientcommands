package net.earthcomputer.clientcommands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
			LOGGER.warn("LongTask timed out");
			setFinished();
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
