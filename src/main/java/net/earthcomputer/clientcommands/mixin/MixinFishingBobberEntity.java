package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.TestRandom;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberEntity.class)
public abstract class MixinFishingBobberEntity extends Entity {
    @Shadow private int hookCountdown;
    @Shadow private FishingBobberEntity.State state;
    @Shadow private int waitCountdown;
    @Shadow private int fishTravelCountdown;
    @Unique private int tickCounter;

    public MixinFishingBobberEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!world.isClient) {
            if (tickCounter == 0) {
                System.out.println("SERVERSIDE SEED: " + PlayerRandCracker.getSeed(random));
            }
            tickCounter++;
            ((TestRandom) random).dump();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void postTick(CallbackInfo ci) {
        if (!world.isClient) {
            if (hookCountdown > 0) {
                System.out.println("Server fishable: " + tickCounter);
            }
            System.out.println("Server: " + state + " " + tickCounter + " " + waitCountdown + " " + fishTravelCountdown);
        }
    }
}
