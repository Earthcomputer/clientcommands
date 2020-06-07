package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.ArmorStandEntityModel;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandEntityRenderer.class)
public abstract class MixinArmorStandEntityRenderer extends LivingEntityRenderer<ArmorStandEntity, ArmorStandEntityModel> {

    public MixinArmorStandEntityRenderer(EntityRenderDispatcher dispatcher, ArmorStandEntityModel model, float shadowSize) {
        super(dispatcher, model, shadowSize);
    }

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void onGetRenderLayer(ArmorStandEntity armorStand, boolean visible, boolean translucent, boolean shouldRenderOutline, CallbackInfoReturnable<RenderLayer> ci) {
        if (((IEntity) armorStand).hasGlowingTicket())
            ci.setReturnValue(super.getRenderLayer(armorStand, visible, translucent, shouldRenderOutline));
    }

}
