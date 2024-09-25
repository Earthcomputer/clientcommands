package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ClientLevelEvents {
    public static final Event<LoadLevel> LOAD_LEVEL = EventFactory.createArrayBacked(LoadLevel.class, listeners -> level -> {
        for (LoadLevel listener : listeners) {
            listener.onLoadLevel(level);
        }
    });

    public static final Event<UnloadLevel> UNLOAD_LEVEL = EventFactory.createArrayBacked(UnloadLevel.class, listeners -> isDisconnect -> {
        for (UnloadLevel listener : listeners) {
            listener.onUnloadLevel(isDisconnect);
        }
    });

    public static final Event<ChunkUpdate> CHUNK_UPDATE = EventFactory.createArrayBacked(ChunkUpdate.class, listeners -> (level, pos, oldState, newState) -> {
        for (ChunkUpdate listener : listeners) {
            listener.onBlockStateUpdate(level, pos, oldState, newState);
        }
    });

    public static final Event<LoadChunk> LOAD_CHUNK = EventFactory.createArrayBacked(LoadChunk.class, listeners -> (level, pos) -> {
        for (LoadChunk listener : listeners) {
            listener.onLoadChunk(level, pos);
        }
    });

    public static final Event<UnloadChunk> UNLOAD_CHUNK = EventFactory.createArrayBacked(UnloadChunk.class, listeners -> (level, pos) -> {
        for (UnloadChunk listener : listeners) {
            listener.onUnloadChunk(level, pos);
        }
    });

    @FunctionalInterface
    public interface LoadLevel {
        void onLoadLevel(ClientLevel level);
    }

    @FunctionalInterface
    public interface UnloadLevel {
        void onUnloadLevel(boolean isDisconnect);
    }

    @FunctionalInterface
    public interface ChunkUpdate {
        void onBlockStateUpdate(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState);
    }

    @FunctionalInterface
    public interface LoadChunk {
        void onLoadChunk(ClientLevel level, ChunkPos chunkPos);
    }

    @FunctionalInterface
    public interface UnloadChunk {
        void onUnloadChunk(ClientLevel level, ChunkPos chunkPos);
    }
}
