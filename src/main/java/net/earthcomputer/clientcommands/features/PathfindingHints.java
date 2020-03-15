package net.earthcomputer.clientcommands.features;

import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public interface PathfindingHints {

    PathNodeType getNodeType(BlockView world, BlockPos pos);

    float getPathfindingPenalty(PathNodeType type);

    float getFollowRange();

    int getReachDistance();

    float getMaxPathLength();

}
