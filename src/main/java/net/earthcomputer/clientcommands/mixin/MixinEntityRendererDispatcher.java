package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.RenderSettings;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRendererDispatcher {

    @Inject(method = "configure", at = @At("HEAD"))
    public void onConfigure(World world, Camera camera, Entity entity, CallbackInfo ci) {
        RenderSettings.preRenderEntities();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    public void redirectShouldRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && !RenderSettings.shouldRenderEntity(entity)) {
            ci.setReturnValue(false);
        }
    }

}
