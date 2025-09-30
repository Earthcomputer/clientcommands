package net.earthcomputer.clientcommands.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;

public final class MoreWorldRenderEvents {
    private MoreWorldRenderEvents() {
    }

    public static final Event<ExtractState> EXTRACT_STATE = EventFactory.createArrayBacked(ExtractState.class, listeners -> (state, camera, deltaTracker) -> {
        for (ExtractState listener : listeners) {
            listener.extractState(state, camera, deltaTracker);
        }
    });

    public static final Event<EndMainPass> END_MAIN_PASS = EventFactory.createArrayBacked(EndMainPass.class, listeners -> (bufferSource, poseStack, state) -> {
        for (EndMainPass listener : listeners) {
            listener.endMainPass(bufferSource, poseStack, state);
        }
    });

    @FunctionalInterface
    public interface ExtractState {
        void extractState(LevelRenderState state, Camera camera, DeltaTracker deltaTracker);
    }

    @FunctionalInterface
    public interface EndMainPass {
        void endMainPass(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, LevelRenderState state);
    }
}
