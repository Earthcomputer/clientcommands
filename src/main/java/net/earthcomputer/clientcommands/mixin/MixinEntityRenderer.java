package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> {

    @Inject(method = "getOutlineColor", at = @At("HEAD"), cancellable = true)
    private void overrideGetOutlineColor(T entity, CallbackInfoReturnable<Integer> ci) {
        if (((IEntity) entity).hasGlowingTicket())
            ci.setReturnValue(((IEntity) entity).getGlowingTicketColor());
    }

}
