package net.earthcomputer.clientcommands.cvw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;

public class ServerConnectorLocal extends ServerConnector {

	private String worldName;
	private String displayName;

	public ServerConnectorLocal(String worldName, String displayName) {
		this.worldName = worldName;
		this.displayName = displayName;
	}

	@Override
	public void connect() {
		Minecraft.getMinecraft().launchIntegratedServer(worldName, displayName, null);
	}

	@Override
	public void disconnect() {
		genericDisconnect();
		Minecraft.getMinecraft().displayGuiScreen(new GuiMainMenu());
	}

	@Override
	public String getDisconnectButtonText() {
		return "menu.returnToMenu";
	}

	@Override
	public boolean canOpenToLan() {
		return !Minecraft.getMinecraft().getIntegratedServer().getPublic();
	}

}
