package net.earthcomputer.clientcommands.cvw;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreenRealmsProxy;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.realms.RealmsScreen;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class ServerConnectorRealms extends ServerConnector {

	private static RealmsServer lastRealmsServer;

	private RealmsServer server;

	private static final Field SELECTED_SERVER_ID_FIELD = ReflectionHelper.findField(RealmsMainScreen.class,
			"selectedServerId");
	private static final Method CONNECT_TO_SERVER_METHOD = ReflectionHelper.findMethod(RealmsMainScreen.class,
			"connectToServer", "connectToServer", RealmsServer.class, RealmsScreen.class);
	private static final Method FIND_SERVER_METHOD = ReflectionHelper.findMethod(RealmsMainScreen.class, "findServer",
			"findServer", long.class);
	static {
		SELECTED_SERVER_ID_FIELD.setAccessible(true);
		CONNECT_TO_SERVER_METHOD.setAccessible(true);
		FIND_SERVER_METHOD.setAccessible(true);
	}

	static void setLastRealmsServer(GuiScreenRealmsProxy proxyScreen, int buttonId) {
		if (proxyScreen.getProxy() instanceof RealmsMainScreen && buttonId == 1) {
			try {
				lastRealmsServer = (RealmsServer) FIND_SERVER_METHOD.invoke(proxyScreen.getProxy(),
						SELECTED_SERVER_ID_FIELD.get(proxyScreen.getProxy()));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static ServerConnectorRealms forCurrentServer() {
		return new ServerConnectorRealms(lastRealmsServer);
	}

	public ServerConnectorRealms(RealmsServer server) {
		this.server = server;
	}

	@Override
	public void connect() {
		GuiMainMenu mainMenu = new GuiMainMenu();
		RealmsBridge bridge = new RealmsBridge();
		bridge.switchToRealms(mainMenu);
		if (!(Minecraft.getMinecraft().currentScreen instanceof GuiScreenRealmsProxy)) {
			return;
		}
		RealmsMainScreen mainScreen = (RealmsMainScreen) ((GuiScreenRealmsProxy) Minecraft.getMinecraft().currentScreen)
				.getProxy();
		try {
			CONNECT_TO_SERVER_METHOD.invoke(mainScreen, server, mainScreen);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void disconnect() {
		genericDisconnect();
		new RealmsBridge().switchToRealms(new GuiMainMenu());
	}

}
