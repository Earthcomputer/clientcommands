package net.earthcomputer.clientcommands.mixin.commands.time;

import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
public class ClientClockInstanceMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void getTimeOfDay(CallbackInfoReturnable<Long> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var overworldClock = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (overworldClock.isEmpty()) {
            return;
        }

        if ((Object) this == level.clockManager().getInstance(overworldClock.get())) {
            long originalTime = cir.getReturnValueJ();
            long modifiedTime = ClientTimeModifier.getModifiedTime(originalTime);
            if (originalTime != modifiedTime) {
                cir.setReturnValue(modifiedTime);
            }
        }
    }
}
