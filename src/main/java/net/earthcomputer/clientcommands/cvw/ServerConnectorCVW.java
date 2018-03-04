package net.earthcomputer.clientcommands.cvw;

import java.io.File;
import java.net.SocketAddress;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.CPacketLoginStart;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.StartupQuery;

public class ServerConnectorCVW extends ServerConnector {

	private int dimensionId;
	private GameType gameType;
	private ServerConnector previousServer;
	private Long2ObjectMap<NBTTagCompound> chunks;
	private NBTTagCompound playerTag;

	public ServerConnectorCVW(int dimensionId, GameType gameType, ServerConnector previousServer,
			Long2ObjectMap<NBTTagCompound> chunks, NBTTagCompound playerTag) {
		this.dimensionId = dimensionId;
		this.gameType = gameType;
		this.previousServer = previousServer;
		this.chunks = chunks;
		this.playerTag = playerTag;
	}

	@Override
	public void connect() {
		Minecraft mc = Minecraft.getMinecraft();
		mc.loadWorld(null);
		System.gc();

		YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(mc.getProxy(),
				UUID.randomUUID().toString());
		MinecraftSessionService sessionService = authService.createMinecraftSessionService();
		GameProfileRepository profileRepo = authService.createProfileRepository();
		PlayerProfileCache profileCache = new PlayerProfileCache(profileRepo,
				new File(mc.mcDataDir, MinecraftServer.USER_CACHE_FILE.getName()));
		TileEntitySkull.setProfileCache(profileCache);
		TileEntitySkull.setSessionService(sessionService);
		PlayerProfileCache.setOnlineMode(false);

		mc.integratedServer = new ClientVirtualServer(mc, dimensionId, gameType, previousServer, chunks, playerTag,
				authService, sessionService, profileRepo, profileCache);
		mc.getIntegratedServer().startServerThread();
		mc.integratedServerIsRunning = true;

		mc.loadingScreen.displaySavingString(I18n.format("menu.loadingLevel"));
		while (!mc.getIntegratedServer().serverIsInRunLoop()) {
			if (!StartupQuery.check()) {
				mc.loadWorld(null);
				mc.displayGuiScreen(null);
				return;
			}

			String message = mc.getIntegratedServer().getUserMessage();
			if (message != null) {
				mc.loadingScreen.displayLoadingString(I18n.format(message));
			} else {
				mc.loadingScreen.displayLoadingString("");
			}

			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		mc.displayGuiScreen(new GuiScreenWorking());
		SocketAddress socketAddress = mc.getIntegratedServer().getNetworkSystem().addLocalEndpoint();
		NetworkManager netManager = NetworkManager.provideLocalClient(socketAddress);
		netManager.setNetHandler(new NetHandlerLoginClient(netManager, mc, null));
		netManager.sendPacket(new C00Handshake(socketAddress.toString(), 0, EnumConnectionState.LOGIN, true));
		GameProfile gameProfile = mc.getSession().getProfile();
		if (!mc.getSession().hasCachedProperties()) {
			gameProfile = mc.getSessionService().fillProfileProperties(gameProfile, true);
			mc.getSession().setProperties(gameProfile.getProperties());
		}
		netManager.sendPacket(new CPacketLoginStart(gameProfile));
		mc.myNetworkManager = netManager;
	}

	@Override
	public void disconnect() {
		genericDisconnect();
		Minecraft.getMinecraft().displayGuiScreen(new GuiMainMenu());
	}

	@Override
	public void onDisconnectButtonPressed() {
		disconnect();
		previousServer.connect();
	}

	@Override
	public String getDisconnectButtonText() {
		return "cvw.toPreviousServer";
	}

}
