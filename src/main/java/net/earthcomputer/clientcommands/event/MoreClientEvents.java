package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

public final class MoreClientEvents {
    public static final Event<TimeSync> TIME_SYNC_ON_NETWORK_THREAD = EventFactory.createArrayBacked(TimeSync.class, listeners -> packet -> {
        for (TimeSync listener : listeners) {
            listener.onTimeSync(packet);
        }
    });
    public static final Event<TimeSync> TIME_SYNC = EventFactory.createArrayBacked(TimeSync.class, listeners -> packet -> {
        for (TimeSync listener : listeners) {
            listener.onTimeSync(packet);
        }
    });
    public static final Event<EstimatedServerTickEvent> ESTIMATED_SERVER_TICK = EventFactory.createArrayBacked(EstimatedServerTickEvent.class, listeners -> () -> {
        Minecraft.getInstance().tell(() -> {
            for (EstimatedServerTickEvent listener : listeners) {
                listener.onEstimatedServerTick();
            }
        });
    });

    @FunctionalInterface
    public interface TimeSync {
        void onTimeSync(ClientboundSetTimePacket packet);
    }

    @FunctionalInterface
    public interface EstimatedServerTickEvent {
        void onEstimatedServerTick();
    }
}
