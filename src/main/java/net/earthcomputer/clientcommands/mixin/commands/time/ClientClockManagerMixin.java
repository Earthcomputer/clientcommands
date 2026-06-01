package net.earthcomputer.clientcommands.mixin.commands.time;

import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void getTimeOfDay(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (definition.is(WorldClocks.OVERWORLD)) {
            long originalTime = cir.getReturnValueJ();
            long modifiedTime = ClientTimeModifier.getModifiedTime(originalTime);
            if (originalTime != modifiedTime) {
                cir.setReturnValue(modifiedTime);
            }
        }
    }
}
