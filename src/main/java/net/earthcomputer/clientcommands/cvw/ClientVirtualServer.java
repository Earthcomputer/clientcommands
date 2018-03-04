package net.earthcomputer.clientcommands.cvw;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.earthcomputer.clientcommands.ClientCommandsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.world.GameType;
import net.minecraft.world.ServerWorldEventHandler;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.storage.AnvilSaveConverter;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public class ClientVirtualServer extends IntegratedServer {

	private static final Logger LOGGER = LogManager.getLogger(ClientCommandsMod.MODID);

	private int dimensionId;
	private GameType gameType;
	private ServerConnector previousServer;
	private Long2ObjectMap<NBTTagCompound> chunks;
	private NBTTagCompound playerTag;
	private ISaveFormat activeAnvilConverter;

	public ClientVirtualServer(Minecraft mc, int dimensionId, GameType gameType, ServerConnector previousServer,
			Long2ObjectMap<NBTTagCompound> chunks, NBTTagCompound playerTag, YggdrasilAuthenticationService authService,
			MinecraftSessionService sessionService, GameProfileRepository profileRepo,
			PlayerProfileCache profileCache) {
		super(mc, "CVW", "CVW", new WorldSettings(0, gameType, false, false, WorldType.FLAT).enableCommands(),
				authService, sessionService, profileRepo, profileCache);
		this.previousServer = previousServer;
		this.dimensionId = dimensionId;
		this.gameType = gameType;
		this.chunks = chunks;
		this.playerTag = playerTag;
		this.activeAnvilConverter = new AnvilSaveConverter(ClientVirtualSaveHandler.DUMMY_DIR,
				Minecraft.getMinecraft().getDataFixer());
	}

	public int getDimensionId() {
		return dimensionId;
	}

	public ServerConnector getPreviousServer() {
		return previousServer;
	}

	public Long2ObjectMap<NBTTagCompound> getChunks() {
		return chunks;
	}

	public NBTTagCompound getPlayerTag() {
		return playerTag;
	}

	@Override
	public void loadAllWorlds(String saveName, String worldNameIn, long seed, WorldType type, String generatorOptions) {
		ClientVirtualWorld overworld = new ClientVirtualWorld(this, 0, worldSettings,
				dimensionId == 0 ? chunks : new Long2ObjectArrayMap<>(0), playerTag);
		overworld.init();
		overworld.initialize(worldSettings);
		overworld.addEventListener(new ServerWorldEventHandler(this, overworld));
		MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(overworld));

		ClientVirtualWorld world;
		if (dimensionId == 0) {
			world = overworld;
		} else {
			world = new ClientVirtualWorldMulti(this, dimensionId, worldSettings, chunks, playerTag, overworld);
			world.init();
			world.addEventListener(new ServerWorldEventHandler(this, world));
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
		}

		getPlayerList().setPlayerManager(new WorldServer[] { overworld });

		setDifficultyForAllWorlds(Minecraft.getMinecraft().gameSettings.difficulty);

		initialWorldChunkLoad();
	}

	@Override
	public void saveAllWorlds(boolean isSilent) {
		LOGGER.info("Skipping world saving of CVW");
	}

	@Override
	public void convertMapIfNeeded(String worldName) {
	}

	@Override
	public CrashReport addServerInfoToCrashReport(CrashReport report) {
		report = super.addServerInfoToCrashReport(report);
		CrashReportCategory category = report.makeCategory("CVW");
		category.addDetail("Dimension ID", () -> String.valueOf(dimensionId));
		category.addDetail("Gamemode", () -> gameType.getName());
		return report;
	}

	@Override
	public String shareToLAN(GameType type, boolean allowCheats) {
		return null;
	}

	@Override
	public ISaveFormat getActiveAnvilConverter() {
		return activeAnvilConverter;
	}

}
