package net.earthcomputer.clientcommands.features;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

public interface PathfindingHints {

    BlockPathTypes getNodeType(BlockGetter world, BlockPos pos);

    float getPathfindingPenalty(BlockPathTypes type);

    float getFollowRange();

    int getReachDistance();

    float getMaxPathLength();

}
