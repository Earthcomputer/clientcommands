package net.earthcomputer.clientcommands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

public final class IntegratedServerUtil {
    private IntegratedServerUtil() {}

    /**
     * Returns the corresponding server player to the client player in singleplayer
     */
    @Nullable
    public static ServerPlayer getServerPlayer() {
        Entity serverEntity = getServerEntityUnchecked(Minecraft.getInstance().player);
        if (serverEntity instanceof ServerPlayer serverPlayer) {
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
        if (entity instanceof Player) {
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
        ServerLevel level = getServerLevel();
        if (level == null) {
            return null;
        }
        return level.getEntity(entity.getId());
    }

    /**
     * Returns the server world for the dimension the player is currently in, in singleplayer
     */
    @Nullable
    public static ServerLevel getServerLevel() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return server.getLevel(level.dimension());
    }

    /**
     * Returns the client entity for a given server entity. Does not work for players
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T getClientEntity(T entity) {
        if (entity instanceof Player) {
            return null;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Entity clientEntity = level.getEntity(entity.getId());
        if (clientEntity != null && clientEntity.getClass() == entity.getClass()) {
            return (T) clientEntity;
        }
        return null;
    }

    /**
     * Gets the random seed of the server player
     */
    public static long getServerPlayerSeed() {
        ServerPlayer player = getServerPlayer();
        if (player == null) {
            return 0;
        }
        return ((LegacyRandomSource) player.getRandom()).seed.get();
    }
}
