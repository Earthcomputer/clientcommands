package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

public class SimulatedWorld extends World {

	private Map<BlockPos, IBlockState> changedBlocks = new LinkedHashMap<>();
	private List<Entity> newEntities = new ArrayList<>();

	private World proxy;

	public SimulatedWorld(World proxy) {
		super(new SaveHandlerMP(), proxy.getWorldInfo(), proxy.provider, proxy.profiler, true);
		this.proxy = proxy;
	}

	@Override
	protected IChunkProvider createChunkProvider() {
		return null;
	}

	@Override
	protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
		return proxy.isChunkGeneratedAt(x, z);
	}

	@Override
	public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
		if (isOutsideBuildHeight(pos)) {
			return false;
		}

		IBlockState oldState = getBlockState(pos);
		if (oldState == newState) {
			return false;
		}
		changedBlocks.put(pos, newState);

		if ((flags & 2) != 0) {
			notifyBlockUpdate(pos, oldState, newState, flags);
		}

		if ((flags & 1) != 0) {
			notifyNeighborsRespectDebug(pos, oldState.getBlock(), true);

			if (newState.hasComparatorInputOverride()) {
				updateComparatorOutputLevel(pos, newState.getBlock());
			}
		}

		if ((flags & 16) == 0) {
			updateObservingBlocksAt(pos, newState.getBlock());
		}

		return true;
	}

	@Override
	public boolean setBlockToAir(BlockPos pos) {
		return setBlockState(pos, Blocks.AIR.getDefaultState());
	}

	@Override
	public boolean setBlockState(BlockPos pos, IBlockState state) {
		return setBlockState(pos, state, 3);
	}

	@Override
	public IBlockState getBlockState(BlockPos pos) {
		IBlockState state = changedBlocks.get(pos);
		return state == null ? proxy.getBlockState(pos) : state;
	}

	@Override
	public boolean spawnEntity(Entity entityIn) {
		return newEntities.add(entityIn);
	}

	public void revert() {
		changedBlocks.clear();
		newEntities.clear();
	}

	public Map<BlockPos, IBlockState> getChangedBlocks() {
		return Collections.unmodifiableMap(changedBlocks);
	}

	public List<Entity> getNewEntities() {
		return Collections.unmodifiableList(newEntities);
	}

}
