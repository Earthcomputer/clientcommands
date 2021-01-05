package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.render.RenderQueue;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copyright (c) 2020 KaptainWutax
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void renderWorldStart(float delta, long time, MatrixStack matrixStack, CallbackInfo ci) {
        RenderQueue.get().setTrackRender(matrixStack);
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void renderWorldFinish(float delta, long time, MatrixStack matrixStack, CallbackInfo ci) {
        RenderQueue.get().setTrackRender(null);
    }

}