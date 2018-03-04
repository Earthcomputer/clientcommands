package net.earthcomputer.clientcommands.cvw;

import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

public class ClientVirtualChunkProvider extends ChunkProviderServer {

	public ClientVirtualChunkProvider(ClientVirtualWorld world, IChunkLoader chunkLoader,
			IChunkGenerator chunkGenerator) {
		super(world, chunkLoader, chunkGenerator);
	}

	@Override
	public boolean tick() {
		world.disableLevelSaving = false;
		boolean ret = super.tick();
		world.disableLevelSaving = true;
		return ret;
	}

}
