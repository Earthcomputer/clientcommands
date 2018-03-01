package net.earthcomputer.clientcommands.cvw;

import net.earthcomputer.clientcommands.EventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreenRealmsProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.MinecraftServer;

public abstract class ServerConnector {

	public static void registerEvents() {
		EventManager.addInitGuiListener(e -> {
			if (e.getGui() instanceof GuiIngameMenu) {
				ServerConnector connector = forCurrentServer();
				e.getButtonList().get(0).displayString = I18n.format(connector.getDisconnectButtonText());
				e.getButtonList().get(4).enabled = connector.canOpenToLan();
			}
		});
		EventManager.addGuiActionPerformedListener(e -> {
			if (e.getGui() instanceof GuiIngameMenu) {
				if (e.getButton().id == 1) {
					e.getButton().playPressSound(Minecraft.getMinecraft().getSoundHandler());
					e.getButton().enabled = false;
					forCurrentServer().disconnect();
					e.setCanceled(true);
				}
			} else if (e.getGui() instanceof GuiScreenRealmsProxy) {
				ServerConnectorRealms.setLastRealmsServer((GuiScreenRealmsProxy) e.getGui(), e.getButton().id);
			}
		});
	}

	public abstract void connect();

	public abstract void disconnect();

	protected static void genericDisconnect() {
		Minecraft mc = Minecraft.getMinecraft();
		mc.world.sendQuittingDisconnectingPacket();
		mc.loadWorld(null);
	}

	public String getDisconnectButtonText() {
		return "menu.disconnect";
	}

	public boolean canOpenToLan() {
		return false;
	}

	public static ServerConnector forCurrentServer() {
		if (Minecraft.getMinecraft().isIntegratedServerRunning()) {
			MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
			return new ServerConnectorLocal(server.getFolderName(), server.getWorldName());
		} else if (Minecraft.getMinecraft().isConnectedToRealms()) {
			return ServerConnectorRealms.forCurrentServer();
		} else {
			return new ServerConnectorRemote(Minecraft.getMinecraft().getCurrentServerData());
		}
	}

}
