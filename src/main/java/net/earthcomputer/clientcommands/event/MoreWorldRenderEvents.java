package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class MoreWorldRenderEvents {
    private MoreWorldRenderEvents() {
    }

    public static final Event<EndMainPass> END_MAIN_PASS = EventFactory.createArrayBacked(EndMainPass.class, callbacks -> context -> {
        for (EndMainPass callback : callbacks) {
            callback.endMainPass(context);
        }
    });

    @FunctionalInterface
    public interface EndMainPass {
        void endMainPass(WorldRenderContext context);
    }
}
