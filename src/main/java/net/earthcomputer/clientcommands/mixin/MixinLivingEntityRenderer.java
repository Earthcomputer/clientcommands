package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {

    protected MixinLivingEntityRenderer(EntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "method_24302", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGlowing()Z"), cancellable = true)
    private void onGetRenderLayer(T entity, boolean visible, boolean translucent, CallbackInfoReturnable<RenderLayer> ci) {
        if (((IEntity) entity).hasGlowingTicket())
            ci.setReturnValue(RenderLayer.getOutline(getTexture(entity)));
    }

}
