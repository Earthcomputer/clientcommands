package net.earthcomputer.clientcommands.features;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkCache;

import java.util.Set;

public class PlayerPathfinder extends PathNodeNavigator {

    private static final PlayerPathNodeMaker NODE_MAKER = new PlayerPathNodeMaker();
    private static final PlayerPathfinder INSTANCE = new PlayerPathfinder();

    private PlayerPathfinder() {
        super(NODE_MAKER, 1);
    }

    public static Path findPathToAny(Set<BlockPos> positions, PathfindingHints hints) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        BlockPos centerPos = new BlockPos(player);
        int range = (int)hints.getFollowRange() + 16;
        ChunkCache chunkCache = new ChunkCache(player.world, centerPos.add(-range, -range, -range), centerPos.add(range, range, range));
        return findPathToAny(chunkCache, player, positions, hints);
    }

    public static Path findPathToAny(ChunkCache world, PlayerEntity player, Set<BlockPos> positions, PathfindingHints hints) {
        NODE_MAKER.initFromPlayer(player, hints);
        return INSTANCE.findPathToAny(world, null, positions, hints.getFollowRange(), hints.getReachDistance(), hints.getMaxPathLength());
    }
}
