package net.earthcomputer.clientcommands.interfaces;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface IBlockChangeListener {

    List<IBlockChangeListener> LISTENERS = new ArrayList<>();

    void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState);

}
