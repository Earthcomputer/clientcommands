package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BowItem.class, TridentItem.class})
public class MixinBowAndTridentItem {

    @Inject(method = "releaseUsing", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;isClientSide:Z"))
    public void onReleaseUsing(ItemStack stack, Level level, LivingEntity thrower, int remainingUseTicks, CallbackInfo ci) {
        PlayerRandCracker.onItemDamage(1, thrower, stack);
    }

}
