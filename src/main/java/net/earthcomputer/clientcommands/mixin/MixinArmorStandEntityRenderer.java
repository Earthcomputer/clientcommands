package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.model.ArmorStandModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public abstract class MixinArmorStandEntityRenderer extends LivingEntityRenderer<ArmorStand, ArmorStandModel> {
    public MixinArmorStandEntityRenderer(EntityRendererProvider.Context ctx, ArmorStandModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "getRenderType(Lnet/minecraft/world/entity/decoration/ArmorStand;ZZZ)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"), cancellable = true)
    private void onGetRenderLayer(ArmorStand armorStand, boolean visible, boolean translucent, boolean shouldRenderOutline, CallbackInfoReturnable<RenderType> ci) {
        if (((IEntity) armorStand).hasGlowingTicket()) {
            ci.setReturnValue(super.getRenderType(armorStand, visible, translucent, shouldRenderOutline));
        }
    }

}
