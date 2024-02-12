package net.earthcomputer.clientcommands.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface IBlockChangeListener {

    List<IBlockChangeListener> LISTENERS = new ArrayList<>();

    void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState);

}
