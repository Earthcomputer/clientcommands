package net.earthcomputer.clientcommands.task;

import net.minecraft.client.gui.GuiScreen;

public abstract class GuiBlocker {

	private boolean finished = false;
	
	public abstract boolean processGui(GuiScreen gui);
	
	public final void setFinished() {
		finished = true;
	}
	
	public final boolean isFinished() {
		return finished;
	}
	
}
