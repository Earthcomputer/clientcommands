package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberEntity.class)
public abstract class MixinFishingBobberEntity extends Entity {
    @Shadow public abstract PlayerEntity getPlayerOwner();

    public MixinFishingBobberEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/entity/projectile/FishingBobberEntity;outOfOpenWaterTicks:I", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false, ordinal = 0))
    private void onBobOutOfWater(CallbackInfo ci) {
        if (FishingCracker.canManipulateFishing() && world.isClient && getPlayerOwner() == MinecraftClient.getInstance().player) {
            FishingCracker.onBobOutOfWater();
        }
    }
}
