package net.earthcomputer.clientcommands.cvw;

import java.io.File;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.advancements.AdvancementManager;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScoreboardSaveData;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.loot.LootTableManager;

public class ClientVirtualWorld extends WorldServer {

	private ClientVirtualServer server;
	private Long2ObjectMap<NBTTagCompound> chunks;

	public ClientVirtualWorld(ClientVirtualServer server, int dimensionId, WorldSettings settings,
			Long2ObjectMap<NBTTagCompound> chunks, NBTTagCompound playerTag) {
		super(server, new ClientVirtualSaveHandler(), new WorldInfo(settings, "CVW"), dimensionId,
				Minecraft.getMinecraft().mcProfiler);
		worldInfo.playerTag = playerTag;
		this.server = server;
		this.chunks = chunks;
		IChunkLoader chunkLoader = new ClientVirtualChunkLoader(chunks);
		IChunkGenerator chunkGenerator = new ClientVirtualChunkGenerator(this, provider.createChunkGenerator());
		this.chunkProvider = new ClientVirtualChunkProvider(this, chunkLoader, chunkGenerator);
		disableLevelSaving = true;
	}

	@Override
	public IChunkProvider createChunkProvider() {
		// Our chunk provider needs information from after the superconstructor
		return null;
	}

	@Override
	public World init() {
		mapStorage = new MapStorage(null);

		String villageCollectionId = VillageCollection.fileNameForProvider(provider);
		villageCollection = new VillageCollection(this);
		mapStorage.setData(villageCollectionId, villageCollection);

		worldScoreboard = new ServerScoreboard(server);
		ScoreboardSaveData scoreboardSaveData = new ScoreboardSaveData();
		mapStorage.setData("scoreboard", scoreboardSaveData);
		scoreboardSaveData.setScoreboard(worldScoreboard);

		lootTable = new LootTableManager(null);
		advancementManager = new AdvancementManager(null);
		functionManager = new FunctionManager(null, server);

		getWorldBorder().setCenter(worldInfo.getBorderCenterX(), worldInfo.getBorderCenterZ());
		getWorldBorder().setDamageAmount(worldInfo.getBorderDamagePerBlock());
		getWorldBorder().setDamageBuffer(worldInfo.getBorderSafeZone());
		getWorldBorder().setWarningDistance(worldInfo.getBorderWarningDistance());
		getWorldBorder().setWarningTime(worldInfo.getBorderWarningTime());
		if (worldInfo.getBorderLerpTime() > 0) {
			getWorldBorder().setTransition(worldInfo.getBorderSize(), worldInfo.getBorderLerpTarget(),
					worldInfo.getBorderLerpTime());
		} else {
			getWorldBorder().setTransition(worldInfo.getBorderSize());
		}

		initCapabilities();

		return this;
	}

	public Long2ObjectMap<NBTTagCompound> getChunks() {
		return chunks;
	}

	@Override
	public File getChunkSaveLocation() {
		return ClientVirtualSaveHandler.DUMMY_DIR;
	}

}
