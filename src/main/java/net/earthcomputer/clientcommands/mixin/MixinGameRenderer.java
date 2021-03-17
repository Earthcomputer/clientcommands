package net.earthcomputer.clientcommands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow @Final private BufferBuilderStorage buffers;
    @Shadow @Final private Camera camera;

    @Inject(method = "renderWorld", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void renderWorldHand(float delta, long time, MatrixStack matrixStack, CallbackInfo ci) {
        matrixStack.push();

        // Render lines through everything
        // TODO: is this the best approach to render through blocks?
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        Vec3d cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        RenderSystem.disableDepthTest();
        RenderQueue.render(RenderQueue.Layer.ON_TOP, matrixStack, buffers.getEntityVertexConsumers(), delta);
        RenderSystem.enableDepthTest();

        matrixStack.pop();
    }
}
