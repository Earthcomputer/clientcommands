package net.earthcomputer.clientcommands.server;

import net.minecraft.server.MinecraftServer;

public interface ServerTask {
    Status tick(MinecraftServer server, long deadline);

    default void onCancelled() {
    }

    enum Status {
        IN_PROGRESS,
        YIELD,
        FINISHED,
    }
}
