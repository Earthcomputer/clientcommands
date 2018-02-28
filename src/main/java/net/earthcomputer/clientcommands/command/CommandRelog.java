package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.task.GuiBlocker;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandRelog extends ClientCommandBase {

	// flag to stop disconnecting event listeners from being triggered by the
	// /crelog command (mostly for convenience)
	public static boolean isRelogging = false;

	@Override
	public void execute(MinecraftServer arg0, ICommandSender arg1, String[] arg2) throws CommandException {
		isRelogging = true;
		Minecraft mc = Minecraft.getMinecraft();
		boolean localWorld = mc.isIntegratedServerRunning();
		ServerData serverData = null;
		String worldName = null;
		String displayName = null;
		if (!localWorld) {
			serverData = mc.getCurrentServerData();
		} else {
			MinecraftServer integratedServer = mc.getIntegratedServer();
			worldName = integratedServer.getFolderName();
			displayName = integratedServer.getWorldName();
		}
		mc.world.sendQuittingDisconnectingPacket();
		mc.loadWorld(null);
		if (!localWorld) {
			mc.displayGuiScreen(new GuiConnecting(new GuiErrorScreen(I18n.format("commands.crelog.errorScreen.line1"),
					I18n.format("commands.crelog.errorScreen.line2")), mc, serverData));
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
		} else {
			mc.launchIntegratedServer(worldName, displayName, null);
		}
	}

	@Override
	public String getName() {
		return "crelog";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.crelog.usage";
	}

}
