package net.earthcomputer.clientcommands.task;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.event.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.function.Predicate;

public abstract class RenderDistanceScanTask extends SimpleTask {
    private static final long MAX_SCAN_TIME = 30_000_000L; // 30ms
    private static final Set<Object> MUTEX_KEYS = Set.of(RenderDistanceScanTask.class);

    static {
        ClientLevelEvents.CHUNK_UPDATE.register((level, pos, oldState, newState) -> {
            WeakReference<RenderDistanceScanTask> currentScanTask = RenderDistanceScanTask.currentScanTask;
            if (currentScanTask != null) {
                RenderDistanceScanTask scanTask = currentScanTask.get();
                if (scanTask != null) {
                    scanTask.onBlockStateUpdate(level, pos, oldState, newState);
                }
            }
        });
        ClientLevelEvents.UNLOAD_CHUNK.register((level, pos) -> {
            WeakReference<RenderDistanceScanTask> currentScanTask = RenderDistanceScanTask.currentScanTask;
            if (currentScanTask != null) {
                RenderDistanceScanTask scanTask = currentScanTask.get();
                if (scanTask != null) {
                    scanTask.onUnloadChunk(level, pos);
                }
            }
        });
        ClientLevelEvents.LOAD_CHUNK.register((level, pos) -> {
            WeakReference<RenderDistanceScanTask> currentScanTask = RenderDistanceScanTask.currentScanTask;
            if (currentScanTask != null) {
                RenderDistanceScanTask scanTask = currentScanTask.get();
                if (scanTask != null) {
                    scanTask.onLoadChunk(level, pos);
                }
            }
        });
    }

    @Nullable
    private static WeakReference<RenderDistanceScanTask> currentScanTask = null;
    protected boolean keepSearching;
    private LongLinkedOpenHashSet remainingChunks;

    protected RenderDistanceScanTask(boolean keepSearching) {
        this.keepSearching = keepSearching;
    }

    @Override
    public void initialize() {
        remainingChunks = new LongLinkedOpenHashSet();
        Entity cameraEntity = Minecraft.getInstance().cameraEntity;
        if (cameraEntity == null) {
            _break();
            return;
        }
        BlockPos.spiralAround(new BlockPos(Mth.floor(cameraEntity.getX()) >> 4, 0, Mth.floor(cameraEntity.getZ()) >> 4), Minecraft.getInstance().options.renderDistance().get(), Direction.EAST, Direction.SOUTH).iterator().forEachRemaining(pos -> remainingChunks.add(ChunkPos.asLong(pos.getX(), pos.getZ())));
        currentScanTask = new WeakReference<>(this);
    }

    @Override
    public boolean condition() {
        return hasChunksRemaining() || keepSearching;
    }

    @Override
    protected final void onTick() {
        try {
            doTick();
        } catch (CommandSyntaxException e) {
            ClientCommandHelper.sendError(ComponentUtils.fromMessage(e.getRawMessage()));
        }
    }

    protected boolean hasChunksRemaining() {
        return !remainingChunks.isEmpty();
    }

    protected void doTick() throws CommandSyntaxException {
        Entity cameraEntity = Minecraft.getInstance().cameraEntity;
        if (cameraEntity == null) {
            _break();
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;

        long startTime = System.nanoTime();
        while (hasChunksRemaining()) {
            ChunkPos chunkPos = new ChunkPos(remainingChunks.removeFirst());

            if (canScanChunk(cameraEntity, chunkPos)) {
                int minSection = level.getMinSection();
                int maxSection = level.getMaxSection();
                for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                    SectionPos sectionPos = SectionPos.of(chunkPos, sectionY);
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
    }

    @Override
    public void onCompleted() {
        currentScanTask = null;
    }

    @Override
    public Set<Object> getMutexKeys() {
        return MUTEX_KEYS;
    }

    protected void onBlockStateUpdate(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (keepSearching) {
            try {
                scanBlock(Minecraft.getInstance().cameraEntity, pos);
            } catch (CommandSyntaxException e) {
                ClientCommandHelper.sendError(ComponentUtils.fromMessage(e.getRawMessage()));
            }
        }
    }

    protected void onLoadChunk(ClientLevel level, ChunkPos pos) {
        if (keepSearching) {
            remainingChunks.add(pos.toLong());
        }
    }

    protected void onUnloadChunk(ClientLevel level, ChunkPos pos) {
        if (keepSearching) {
            remainingChunks.add(pos.toLong());
        }
    }

    protected boolean canScanChunk(Entity cameraEntity, ChunkPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        return level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null;
    }

    protected boolean canScanChunkSection(Entity cameraEntity, SectionPos pos) {
        return true;
    }

    protected boolean hasAnyBlockCloserThan(Entity cameraEntity, ChunkPos chunkPos, double distanceSq) {
        Vec3 cameraPos = cameraEntity.getEyePosition(0);
        double closestX = Mth.clamp(cameraPos.x, chunkPos.x << 4, (chunkPos.x + 1) << 4);
        double closestZ = Mth.clamp(cameraPos.z, chunkPos.z << 4, (chunkPos.z + 1) << 4);
        double closestDistanceSq = Mth.square(cameraPos.x - closestX) + Mth.square(cameraPos.z - closestZ);
        return closestDistanceSq < distanceSq;
    }

    protected boolean willScanBlockCloserThan(Entity cameraEntity, ChunkPos currentlyScanningChunk, double distanceSq) {
        Vec3 cameraPos = cameraEntity.getEyePosition(0);
        int cameraChunkX = Mth.floor(cameraPos.x) >> 4;
        int cameraChunkZ = Mth.floor(cameraPos.z) >> 4;
        int chunkRadius = Math.max(Math.abs(currentlyScanningChunk.x - cameraChunkX), Math.abs(currentlyScanningChunk.z - cameraChunkZ));
        double minPossibleDistance = ((chunkRadius - 1) << 4) + Math.min(
            Math.min(cameraPos.x - (cameraChunkX << 4), ((cameraChunkX + 1) << 4) - cameraPos.x),
            Math.min(cameraPos.z - (cameraChunkZ << 4), ((cameraChunkZ + 1) << 4) - cameraPos.z)
        );
        return minPossibleDistance * minPossibleDistance < distanceSq;
    }

    protected boolean hasBlockState(SectionPos pos, Predicate<BlockState> stateTest) {
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        LevelChunk chunk = level.getChunk(pos.getX(), pos.getZ());
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(pos.getY()));
        return section.maybeHas(stateTest);
    }

    private void scanChunkSection(Entity cameraEntity, SectionPos sectionPos) throws CommandSyntaxException {
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        for (BlockPos pos : BlockPos.betweenClosed(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ(), sectionPos.maxBlockX(), sectionPos.maxBlockY(), sectionPos.maxBlockZ())) {
            scanBlock(cameraEntity, pos);
        }
    }

    protected abstract void scanBlock(Entity cameraEntity, BlockPos pos) throws CommandSyntaxException;
}
