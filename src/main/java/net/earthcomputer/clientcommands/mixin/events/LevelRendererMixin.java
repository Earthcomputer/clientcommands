package net.earthcomputer.clientcommands.mixin.events;

import net.earthcomputer.clientcommands.event.MoreWorldRenderEvents;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "lambda$addMainPass$2", at = @At(value = "INVOKE:LAST", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void onEndMainPass(CallbackInfo ci) {
        MoreWorldRenderEvents.END_MAIN_PASS.invoker().endMainPass(RenderQueue.getWorldRenderContext((LevelRenderer) (Object) this));
    }
}
