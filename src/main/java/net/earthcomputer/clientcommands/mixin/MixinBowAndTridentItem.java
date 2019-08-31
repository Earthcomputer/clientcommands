package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BowItem.class, TridentItem.class})
public class MixinBowAndTridentItem {

    @Inject(method = "onStoppedUsing", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isClient:Z"))
    public void onOnStoppedUsing(ItemStack stack, World world, LivingEntity thrower, int remainingUseTicks, CallbackInfo ci) {
        PlayerRandCracker.onItemDamage(1, thrower, stack);
    }

}
