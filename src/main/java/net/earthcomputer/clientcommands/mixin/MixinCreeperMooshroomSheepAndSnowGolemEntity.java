package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CreeperEntity.class, MooshroomEntity.class, SheepEntity.class, SnowGolemEntity.class})
public class MixinCreeperMooshroomSheepAndSnowGolemEntity {

    @Inject(method = "interactMob", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isClient:Z"))
    public void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<Boolean> ci) {
        EnchantmentCracker.onItemDamage(1, player, player.getStackInHand(hand));
    }

}
