package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.ArmorStandEntityModel;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandEntityRenderer.class)
public abstract class MixinArmorStandEntityRenderer extends LivingEntityRenderer<ArmorStandEntity, ArmorStandEntityModel> {
    public MixinArmorStandEntityRenderer(EntityRendererFactory.Context ctx, ArmorStandEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "getRenderLayer(Lnet/minecraft/entity/decoration/ArmorStandEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;", at = @At("HEAD"), cancellable = true)
    private void onGetRenderLayer(ArmorStandEntity armorStand, boolean visible, boolean translucent, boolean shouldRenderOutline, CallbackInfoReturnable<RenderLayer> ci) {
        if (((IEntity) armorStand).hasGlowingTicket()) {
            ci.setReturnValue(super.getRenderLayer(armorStand, visible, translucent, shouldRenderOutline));
        }
    }

}
