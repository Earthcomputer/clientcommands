package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.multiplayer.ClientLevel;

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

    @FunctionalInterface
    public interface LoadLevel {
        void onLoadLevel(ClientLevel level);
    }

    @FunctionalInterface
    public interface UnloadLevel {
        void onUnloadLevel(boolean isDisconnect);
    }
}
