package net.earthcomputer.clientcommands.cvw;

import java.io.IOException;
import java.lang.reflect.Method;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class ClientVirtualChunkLoader implements IChunkLoader {

	private static final AnvilChunkLoader ANVIL_CHUNK_LOADER = new AnvilChunkLoader(null, null);

	private static final Method READ_FROM_NBT_METHOD = ReflectionHelper.findMethod(AnvilChunkLoader.class,
			"checkedReadChunkFromNBT__Async", "checkedReadChunkFromNBT__Async", World.class, int.class, int.class,
			NBTTagCompound.class);

	private Long2ObjectMap<NBTTagCompound> chunks;

	public ClientVirtualChunkLoader(Long2ObjectMap<NBTTagCompound> chunks) {
		this.chunks = chunks;
	}

	public static Long2ObjectMap<NBTTagCompound> generateChunkMap(WorldClient world,
			ChunkProviderClient chunkProvider) {
		Long2ObjectMap<NBTTagCompound> chunkMap = new Long2ObjectOpenHashMap<>();
		chunkProvider.chunkMapping.forEach((pos, chunk) -> {
			NBTTagCompound nbt = new NBTTagCompound();
			NBTTagCompound levelTag = new NBTTagCompound();
			nbt.setTag("Level", levelTag);
			nbt.setInteger("DataVersion", 1343);
			FMLCommonHandler.instance().getDataFixer().writeVersionData(nbt);
			ANVIL_CHUNK_LOADER.writeChunkToNBT(chunk, world, levelTag);
			ForgeChunkManager.storeChunkNBT(chunk, levelTag);
			MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, nbt));
			chunkMap.put(pos, nbt);
		});
		return chunkMap;
	}

	@Override
	public Chunk loadChunk(World world, int x, int z) throws IOException {
		NBTTagCompound nbt = chunks.get(ChunkPos.asLong(x, z));
		if (nbt == null) {
			return null;
		}

		Object[] ret;
		try {
			ret = (Object[]) READ_FROM_NBT_METHOD.invoke(ANVIL_CHUNK_LOADER, world, x, z, nbt);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (ret != null) {
			Chunk chunk = (Chunk) ret[0];
			nbt = (NBTTagCompound) ret[1];
			ANVIL_CHUNK_LOADER.loadEntities(world, nbt.getCompoundTag("Level"), chunk);
			return chunk;
		}

		return null;
	}

	@Override
	public void saveChunk(World worldIn, Chunk chunkIn) throws MinecraftException, IOException {
	}

	@Override
	public void saveExtraChunkData(World worldIn, Chunk chunkIn) throws IOException {
	}

	@Override
	public void chunkTick() {
	}

	@Override
	public void flush() {
	}

	@Override
	public boolean isChunkGeneratedAt(int x, int z) {
		return chunks.containsKey(ChunkPos.asLong(x, z));
	}

}
