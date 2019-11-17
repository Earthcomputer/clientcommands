package net.earthcomputer.clientcommands.interfaces;

import net.minecraft.block.BlockState;

public interface IFireBlock {

    int callGetBurnChance(BlockState state);

    int callGetSpreadChance(BlockState state);

}
