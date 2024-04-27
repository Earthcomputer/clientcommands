package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {
    protected LivingEntityRendererMixin(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void onGetRenderType(T entity, boolean visible, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderType> ci) {
        if (ci.getReturnValue() == null && ((IEntity) entity).hasGlowingTicket()) {
            ci.setReturnValue(RenderType.outline(getTextureLocation(entity)));
        }
    }

}
