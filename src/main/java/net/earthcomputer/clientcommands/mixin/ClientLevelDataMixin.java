package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class ClientLevelDataMixin {

    @Inject(at = @At("TAIL"), method = "getDayTime()J", cancellable = true)
    public void getTimeOfDay(CallbackInfoReturnable<Long> info) {
        var timeOfDay = info.getReturnValue();
        long modifiedTimeOfDay = ClientTimeModifier.getModifiedTime(timeOfDay);
        info.setReturnValue(modifiedTimeOfDay);
    }
}
