package net.earthcomputer.clientcommands.mixin.commands.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.earthcomputer.clientcommands.features.RenderSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRendererDispatcherMixin {

    @Inject(method = "prepare", at = @At("HEAD"))
    public void onPrepare(Camera camera, Entity entity, CallbackInfo ci) {
        RenderSettings.preRenderEntities();
    }

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    public boolean redirectShouldRender(boolean original, Entity entity) {
        if (original && !RenderSettings.shouldRenderEntity(entity)) {
            return false;
        }

        return original;
    }

}
