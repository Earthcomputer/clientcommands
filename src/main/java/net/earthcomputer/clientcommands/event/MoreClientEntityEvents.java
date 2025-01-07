package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;

public final class MoreClientEntityEvents {
    /**
     * Called twice, first on the network thread and then on the main thread
     */
    public static final Event<AddEntity> PRE_ADD_MAYBE_ON_NETWORK_THREAD = EventFactory.createArrayBacked(AddEntity.class, listeners -> packet -> {
        for (AddEntity listener : listeners) {
            listener.onAddEntity(packet);
        }
    });

    public static final Event<AddEntity> POST_ADD = EventFactory.createArrayBacked(AddEntity.class, listeners -> packet -> {
        for (AddEntity listener : listeners) {
            listener.onAddEntity(packet);
        }
    });

    /**
     * Because for some reason this is separate from adding a regular entity
     */
    public static final Event<AddXpOrb> POST_ADD_XP_ORB = EventFactory.createArrayBacked(AddXpOrb.class, listeners -> packet -> {
        for (AddXpOrb listener : listeners) {
            listener.onXpOrb(packet);
        }
    });

    @FunctionalInterface
    public interface AddEntity {
        void onAddEntity(ClientboundAddEntityPacket packet);
    }

    @FunctionalInterface
    public interface AddXpOrb {
        void onXpOrb(ClientboundAddExperienceOrbPacket packet);
    }
}
