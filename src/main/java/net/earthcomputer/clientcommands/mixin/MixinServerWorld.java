package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class MixinServerWorld {
    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void onTickEntity(Entity entity, CallbackInfo ci) {
        ((IEntity) entity).tickDebugRandom();
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void onTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ((IEntity) passenger).tickDebugRandom();
    }
}
