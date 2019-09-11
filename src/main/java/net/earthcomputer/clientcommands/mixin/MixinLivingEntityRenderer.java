package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow protected boolean disableOutlineRender;

    @Redirect(method = "method_4054", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;disableOutlineRender:Z"))
    private boolean redirectDisableOutlineRender(LivingEntityRenderer _this, T entity, double x, double y, double z, float yaw, float pitch) {
        return this.disableOutlineRender && !((IEntity) entity).hasGlowingTicket();
    }

}
