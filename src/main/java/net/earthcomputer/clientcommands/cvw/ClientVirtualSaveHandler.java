package net.earthcomputer.clientcommands.cvw;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServerDemo;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.ForgeEventFactory;

public class ClientVirtualSaveHandler extends SaveHandler {

	public static final File DUMMY_DIR = new File(Minecraft.getMinecraft().mcDataDir, "cvwWorldDir");

	static {
		DUMMY_DIR.mkdirs();
		DUMMY_DIR.deleteOnExit();
	}

	public ClientVirtualSaveHandler() {
		super(DUMMY_DIR, "CVW", false, Minecraft.getMinecraft().getDataFixer());
	}

	@Override
	public WorldInfo loadWorldInfo() {
		return new WorldInfo(WorldServerDemo.DEMO_WORLD_SETTINGS, "CVW");
	}

	@Override
	public void checkSessionLock() throws MinecraftException {
	}

	@Override
	public IChunkLoader getChunkLoader(WorldProvider provider) {
		throw new UnsupportedOperationException("This should only be called on world creation");
	}

	@Override
	public void saveWorldInfoWithPlayer(WorldInfo worldInformation, NBTTagCompound tagCompound) {
	}

	@Override
	public void saveWorldInfo(WorldInfo worldInformation) {
	}

	@Override
	public IPlayerFileData getPlayerNBTManager() {
		return this;
	}

	@Override
	public void flush() {
	}

	@Override
	public File getWorldDirectory() {
		return DUMMY_DIR;
	}

	@Override
	public File getMapFileFromName(String mapName) {
		return new File(DUMMY_DIR, mapName + ".dat");
	}

	@Override
	public TemplateManager getStructureTemplateManager() {
		return new TemplateManager(null, Minecraft.getMinecraft().getDataFixer()) {
			@Override
			public boolean readTemplate(ResourceLocation server) {
				return false;
			}

			@Override
			public boolean writeTemplate(MinecraftServer server, ResourceLocation id) {
				return false;
			}
		};
	}

	private Map<UUID, NBTTagCompound> playerData = new HashMap<>();

	@Override
	public void writePlayerData(EntityPlayer player) {
		playerData.put(player.getUniqueID(), player.writeToNBT(new NBTTagCompound()));
		ForgeEventFactory.firePlayerSavingEvent(player, new File(DUMMY_DIR, "players"),
				player.getUniqueID().toString());
	}

	@Override
	public NBTTagCompound readPlayerData(EntityPlayer player) {
		NBTTagCompound nbt = playerData.get(player.getUniqueID());
		if (nbt != null) {
			player.readFromNBT(nbt);
		}
		ForgeEventFactory.firePlayerLoadingEvent(player, new File(DUMMY_DIR, "players"),
				player.getUniqueID().toString());
		return nbt;
	}

	@Override
	public String[] getAvailablePlayerDat() {
		return playerData.keySet().stream().map(UUID::toString).toArray(String[]::new);
	}

}
