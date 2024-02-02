package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.RenderSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRendererDispatcher {

    @Inject(method = "prepare", at = @At("HEAD"))
    public void onConfigure(Level world, Camera camera, Entity entity, CallbackInfo ci) {
        RenderSettings.preRenderEntities();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    public void redirectShouldRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && !RenderSettings.shouldRenderEntity(entity)) {
            ci.setReturnValue(false);
        }
    }

}
