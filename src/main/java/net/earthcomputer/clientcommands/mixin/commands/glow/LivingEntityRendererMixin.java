package net.earthcomputer.clientcommands.mixin.commands.glow;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.earthcomputer.clientcommands.interfaces.IEntity_Glowable;
import net.earthcomputer.clientcommands.interfaces.ILivingEntityRenderState_Glowable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {
    @Shadow
    public abstract Identifier getTextureLocation(S renderState);

    protected LivingEntityRendererMixin(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    @Nullable
    private RenderType onGetRenderType(@Nullable RenderType original, S renderState) {
        if (original == null && ((ILivingEntityRenderState_Glowable) renderState).clientcommands_hasGlowingTicket()) {
            return RenderTypes.outline(getTextureLocation(renderState));
        }

        return original;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void extractGlowingRenderState(T entity, S renderState, float tickDelta, CallbackInfo ci) {
        ((ILivingEntityRenderState_Glowable) renderState).clientcommands_setHasGlowingTicket(((IEntity_Glowable) entity).clientcommands_hasGlowingTicket());
    }
}
