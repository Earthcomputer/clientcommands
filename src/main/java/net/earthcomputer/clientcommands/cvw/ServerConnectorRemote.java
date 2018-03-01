package net.earthcomputer.clientcommands.cvw;

import net.earthcomputer.clientcommands.task.GuiBlocker;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

public class ServerConnectorRemote extends ServerConnector {

	private ServerData serverData;

	public ServerConnectorRemote(ServerData serverData) {
		this.serverData = serverData;
	}

	@Override
	public void connect() {
		Minecraft mc = Minecraft.getMinecraft();
		mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, serverData));
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
	public void disconnect() {
		genericDisconnect();
		Minecraft.getMinecraft().displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
	}

}
