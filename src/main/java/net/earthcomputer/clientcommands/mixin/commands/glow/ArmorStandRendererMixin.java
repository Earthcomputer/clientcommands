package net.earthcomputer.clientcommands.mixin.commands.glow;

import net.earthcomputer.clientcommands.interfaces.ILivingEntityRenderState_Glowable;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.model.ArmorStandModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin extends LivingEntityRenderer<ArmorStand, ArmorStandRenderState, ArmorStandArmorModel> {
    public ArmorStandRendererMixin(EntityRendererProvider.Context ctx, ArmorStandModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;ZZZ)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(ArmorStandRenderState renderState, boolean visible, boolean translucent, boolean shouldRenderOutline, CallbackInfoReturnable<RenderType> ci) {
        if (((ILivingEntityRenderState_Glowable) renderState).clientcommands_hasGlowingTicket()) {
            ci.setReturnValue(super.getRenderType(renderState, visible, translucent, shouldRenderOutline));
        }
    }
}
