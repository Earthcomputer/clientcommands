package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ClientConnectionEvents {
    public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, listeners -> () -> {
        for (Disconnect listener : listeners) {
            listener.onDisconnect();
        }
    });

    @FunctionalInterface
    public interface Disconnect {
        void onDisconnect();
    }
}
