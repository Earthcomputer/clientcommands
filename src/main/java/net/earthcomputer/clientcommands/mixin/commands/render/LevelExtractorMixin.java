package net.earthcomputer.clientcommands.mixin.commands.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.features.RenderSettings;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Inject(method = "isEntityVisible", at = @At("RETURN"), cancellable = true)
    private void hasIndirectPassengersAndClientCommandsShouldRender(CallbackInfoReturnable<Boolean> cir, @Local(name = "entity", argsOnly = true) Entity entity) {
        if (cir.getReturnValueZ() && !RenderSettings.shouldRenderEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
