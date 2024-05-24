package net.earthcomputer.clientcommands.mixin.debug;

import net.earthcomputer.clientcommands.interfaces.IEntity_Debug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        ((IEntity_Debug) entity).clientcommands_tickDebugRandom();
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void onTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ((IEntity_Debug) passenger).clientcommands_tickDebugRandom();
    }
}
