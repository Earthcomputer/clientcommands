package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

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

    @Redirect(method = "doWaterSplashEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"))
    private void playSound(Entity instance, SoundEvent sound, float volume, float pitch) {
        if ((Object) this instanceof Villager villager && !instance.level().isClientSide) {
            System.out.println(Arrays.toString(villager.getBrain().getActiveActivities().toArray(Activity[]::new)));
//            System.out.println("Villager splash sound pitch: " + pitch);
        }
        instance.playSound(sound, volume, pitch);
    }
}
