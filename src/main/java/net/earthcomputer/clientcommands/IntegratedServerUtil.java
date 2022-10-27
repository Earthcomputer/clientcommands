package net.earthcomputer.clientcommands;

import net.earthcomputer.clientcommands.mixin.CheckedRandomAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public final class IntegratedServerUtil {
    private IntegratedServerUtil() {}

    /**
     * Returns the corresponding server player to the client player in singleplayer
     */
    @Nullable
    public static ServerPlayerEntity getServerPlayer() {
        Entity serverEntity = getServerEntityUnchecked(MinecraftClient.getInstance().player);
        if (serverEntity instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    /**
     * Returns the corresponding server entity to the client entity in singleplayer.
     * Does not work for players.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T getServerEntity(T entity) {
        if (entity instanceof PlayerEntity) {
            return null;
        }

        Entity serverEntity = getServerEntityUnchecked(entity);
        if (serverEntity != null && serverEntity.getClass() == entity.getClass()) {
            return (T) serverEntity;
        }
        return null;
    }

    @Nullable
    private static Entity getServerEntityUnchecked(Entity entity) {
        ServerWorld world = getServerWorld();
        if (world == null) {
            return null;
        }
        return world.getEntityById(entity.getId());
    }

    /**
     * Returns the server world for the dimension the player is currently in, in singleplayer
     */
    @Nullable
    public static ServerWorld getServerWorld() {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            return null;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        return server.getWorld(world.getRegistryKey());
    }

    /**
     * Returns the client entity for a given server entity. Does not work for players
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T getClientEntity(T entity) {
        if (entity instanceof PlayerEntity) {
            return null;
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        Entity clientEntity = world.getEntityById(entity.getId());
        if (clientEntity != null && clientEntity.getClass() == entity.getClass()) {
            return (T) clientEntity;
        }
        return null;
    }

    /**
     * Gets the random seed of the server player
     */
    public static long getServerPlayerSeed() {
        ServerPlayerEntity player = getServerPlayer();
        if (player == null) {
            return 0;
        }
        return ((CheckedRandomAccessor) player.getRandom()).getSeed().get();
    }
}
