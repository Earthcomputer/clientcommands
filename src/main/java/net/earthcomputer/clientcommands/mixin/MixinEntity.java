package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "onSwimmingStart", at = @At("HEAD"))
    public void onOnSwimmingStart(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onSwimmingStart();
    }

    @Inject(method = "spawnSprintingParticles", at = @At("HEAD"))
    public void onSprinting(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onSprinting();
    }

    private boolean isThePlayer() {
        //noinspection ConstantConditions
        return (Object) this instanceof ClientPlayerEntity;
    }

}
