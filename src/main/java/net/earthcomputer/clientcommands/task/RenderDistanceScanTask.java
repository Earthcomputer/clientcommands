package net.earthcomputer.clientcommands.task;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Iterator;
import java.util.function.Predicate;

public abstract class RenderDistanceScanTask extends SimpleTask {
    private static final long MAX_SCAN_TIME = 30_000_000L; // 30ms

    private final boolean keepSearching;

    private Iterator<BlockPos.Mutable> squarePosIterator;

    protected RenderDistanceScanTask(boolean keepSearching) {
        this.keepSearching = keepSearching;
    }

    @Override
    public void initialize() {
        squarePosIterator = createIterator();
    }

    @Override
    public boolean condition() {
        return squarePosIterator != null;
    }

    @Override
    protected void onTick() {
        Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;
        if (cameraEntity == null) {
            _break();
            return;
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;

        long startTime = System.nanoTime();
        while (squarePosIterator.hasNext()) {
            BlockPos chunkPosAsBlockPos = squarePosIterator.next();
            ChunkPos chunkPos = new ChunkPos(chunkPosAsBlockPos.getX(), chunkPosAsBlockPos.getZ());

            if (canScanChunk(cameraEntity, chunkPos)) {
                int minSection = world.getBottomSectionCoord();
                int maxSection = world.getTopSectionCoord();
                for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                    ChunkSectionPos sectionPos = ChunkSectionPos.from(chunkPos, sectionY);
                    if (canScanChunkSection(cameraEntity, sectionPos)) {
                        scanChunkSection(cameraEntity, sectionPos);
                    }
                }
            }

            if (System.nanoTime() - startTime > MAX_SCAN_TIME) {
                // wait till next tick
                return;
            }
        }

        if (keepSearching) {
            if (canKeepSearchingNow()) {
                squarePosIterator = createIterator();
            }
        } else {
            squarePosIterator = null;
        }
    }

    protected boolean canKeepSearchingNow() {
        return true;
    }

    protected boolean canScanChunk(Entity cameraEntity, ChunkPos pos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;
        return world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null;
    }

    protected boolean canScanChunkSection(Entity cameraEntity, ChunkSectionPos pos) {
        return true;
    }

    protected boolean hasAnyBlockCloserThan(Entity cameraEntity, ChunkPos chunkPos, double distanceSq) {
        Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
        double closestX = MathHelper.clamp(cameraPos.x, chunkPos.x << 4, (chunkPos.x + 1) << 4);
        double closestZ = MathHelper.clamp(cameraPos.z, chunkPos.z << 4, (chunkPos.z + 1) << 4);
        double closestDistanceSq = MathHelper.square(cameraPos.x - closestX) + MathHelper.square(cameraPos.z - closestZ);
        return closestDistanceSq < distanceSq;
    }

    protected boolean willScanBlockCloserThan(Entity cameraEntity, ChunkPos currentlyScanningChunk, double distanceSq) {
        Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
        int cameraChunkX = MathHelper.floor(cameraPos.x) >> 4;
        int cameraChunkZ = MathHelper.floor(cameraPos.z) >> 4;
        int chunkRadius = Math.max(Math.abs(currentlyScanningChunk.x - cameraChunkX), Math.abs(currentlyScanningChunk.z - cameraChunkZ));
        double minPossibleDistance = ((chunkRadius - 1) << 4) + Math.min(
            Math.min(cameraPos.x - (cameraChunkX << 4), ((cameraChunkX + 1) << 4) - cameraPos.x),
            Math.min(cameraPos.z - (cameraChunkZ << 4), ((cameraChunkZ + 1) << 4) - cameraPos.z)
        );
        return minPossibleDistance * minPossibleDistance < distanceSq;
    }

    protected boolean hasBlockState(ChunkSectionPos pos, Predicate<BlockState> stateTest) {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;
        WorldChunk chunk = world.getChunk(pos.getX(), pos.getZ());
        ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(pos.getY()));
        return section.hasAny(stateTest);
    }

    private void scanChunkSection(Entity cameraEntity, ChunkSectionPos sectionPos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;
        for (BlockPos pos : BlockPos.iterate(sectionPos.getMinX(), sectionPos.getMinY(), sectionPos.getMinZ(), sectionPos.getMaxX(), sectionPos.getMaxY(), sectionPos.getMaxZ())) {
            scanBlock(cameraEntity, pos);
        }
    }

    protected abstract void scanBlock(Entity cameraEntity, BlockPos pos);

    private Iterator<BlockPos.Mutable> createIterator() {
        Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;
        if (cameraEntity == null) {
            _break();
            return null;
        }
        return BlockPos.iterateInSquare(new BlockPos(MathHelper.floor(cameraEntity.getX()) >> 4, 0, MathHelper.floor(cameraEntity.getZ()) >> 4), MinecraftClient.getInstance().options.getViewDistance().getValue(), Direction.EAST, Direction.SOUTH).iterator();
    }
}
