package net.earthcomputer.clientcommands.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.minecraft.client.world.ClientWorld.Properties;

@Mixin(Properties.class)
public class MixinClientWorldProperties {

    @Inject(at = @At("TAIL"), method = "getTimeOfDay()J", cancellable = true)
    public void getTimeOfDay(CallbackInfoReturnable<Long> info) {
        var timeOfDay = info.getReturnValue();
        long modifiedTimeOfDay = ClientTimeModifier.getModifiedTime(timeOfDay);
        info.setReturnValue(modifiedTimeOfDay);
    }
}
