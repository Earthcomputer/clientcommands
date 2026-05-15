package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.ExperienceOrb;

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

    public static final Event<SetInitialXpOrbValue> POST_SET_INITIAL_XP_ORB_VALUE = EventFactory.createArrayBacked(SetInitialXpOrbValue.class, listeners -> orb -> {
        for (SetInitialXpOrbValue listener : listeners) {
            listener.onSetInitialXpOrbValue(orb);
        }
    });

    @FunctionalInterface
    public interface AddEntity {
        void onAddEntity(ClientboundAddEntityPacket packet);
    }

    @FunctionalInterface
    public interface SetInitialXpOrbValue {
        void onSetInitialXpOrbValue(ExperienceOrb orb);
    }
}
