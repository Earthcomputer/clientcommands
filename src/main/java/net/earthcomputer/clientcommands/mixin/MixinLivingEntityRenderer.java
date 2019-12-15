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
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow protected abstract boolean method_4056(T livingEntity_1, boolean boolean_1);

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;method_4056(Lnet/minecraft/entity/LivingEntity;Z)Z"))
    private boolean redirectDisableOutlineRender(LivingEntityRenderer _this, T entity, boolean flag) {
        return this.method_4056(entity, flag) || ((IEntity) entity).hasGlowingTicket();
    }

}
