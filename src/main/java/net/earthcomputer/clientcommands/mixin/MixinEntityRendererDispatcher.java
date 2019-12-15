package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.RenderSettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRendererDispatcher {

    @Inject(method = "configure", at = @At("HEAD"))
    public void onConfigure(World world, Camera camera, Entity entity, CallbackInfo ci) {
        RenderSettings.preRenderEntities();
    }

    @Redirect(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    public boolean redirectShouldRender(EntityRenderer<Entity> entityRenderer, Entity entity, Frustum frustum, double x, double y, double z) {
        return entityRenderer.shouldRender(entity, frustum, x, y, z) && RenderSettings.shouldRenderEntity(entity);
    }

}
