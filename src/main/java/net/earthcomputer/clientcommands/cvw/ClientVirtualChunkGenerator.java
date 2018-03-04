package net.earthcomputer.clientcommands.cvw;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.gen.IChunkGenerator;

public class ClientVirtualChunkGenerator implements IChunkGenerator {

	private World world;
	private IChunkGenerator proxy;

	public ClientVirtualChunkGenerator(World world, IChunkGenerator proxy) {
		this.world = world;
		this.proxy = proxy;
	}

	@Override
	public Chunk generateChunk(int x, int z) {
		return new EmptyChunk(world, x, z);
	}

	@Override
	public void populate(int x, int z) {
	}

	@Override
	public boolean generateStructures(Chunk chunkIn, int x, int z) {
		return false;
	}

	@Override
	public List<SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
		return proxy.getPossibleCreatures(creatureType, pos);
	}

	@Override
	public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position,
			boolean findUnexplored) {
		return null;
	}

	@Override
	public void recreateStructures(Chunk chunkIn, int x, int z) {
	}

	@Override
	public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
		return false;
	}

}
