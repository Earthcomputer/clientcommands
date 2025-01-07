package net.earthcomputer.clientcommands.mixin.commands.glow;

import net.earthcomputer.clientcommands.interfaces.IEntity_Glowable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        ((IEntity_Glowable) entity).clientcommands_tickGlowingTickets();
    }
}
