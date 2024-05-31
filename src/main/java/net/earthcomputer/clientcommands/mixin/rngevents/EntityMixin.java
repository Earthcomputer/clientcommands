package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    public void onDoWaterSplashEffect(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onSwimmingStart();
        }
    }

    @Inject(method = "playAmethystStepSound", at = @At("HEAD"))
    private void onPlayAmethystStepSound(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onAmethystChime();
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"))
    public void onSprinting(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onSprinting();
        }
    }

    @Unique
    private boolean isThePlayer() {
        return (Object) this instanceof LocalPlayer;
    }
}
