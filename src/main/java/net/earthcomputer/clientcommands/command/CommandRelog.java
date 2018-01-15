package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.task.GuiBlocker;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandRelog extends ClientCommandBase {

	// flag to stop disconnecting event listeners from being triggered by the
	// /crelog command (mostly for convenience)
	public static boolean isRelogging = false;

	@Override
	public void execute(MinecraftServer arg0, ICommandSender arg1, String[] arg2) throws CommandException {
		if (Minecraft.getMinecraft().isIntegratedServerRunning()) {
			throw new CommandException("This command only works on a remote server");
		}

		isRelogging = true;
		Minecraft mc = Minecraft.getMinecraft();
		ServerData serverData = mc.getCurrentServerData();
		mc.world.sendQuittingDisconnectingPacket();
		mc.loadWorld(null);
		mc.displayGuiScreen(new GuiConnecting(new GuiErrorScreen("Auto-Relog", "Failed to connect"), mc, serverData));
		// At some point in the relogging process, mc.displayGuiScreen(null) is called,
		// which happens to open the main menu screen. We don't want this, so an
		// unfortunate hacky solution is to block it once.
		TaskManager.addGuiBlocker(new GuiBlocker() {
			@Override
			public boolean processGui(GuiScreen gui) {
				if (gui instanceof GuiMainMenu) {
					setFinished();
					return false;
				} else {
					return true;
				}
			}
		});
	}

	@Override
	public String getName() {
		return "crelog";
	}

	@Override
	public String getUsage(ICommandSender arg0) {
		return "/crelog";
	}

}
