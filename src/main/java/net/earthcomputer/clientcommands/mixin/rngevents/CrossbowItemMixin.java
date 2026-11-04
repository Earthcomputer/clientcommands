package net.earthcomputer.clientcommands.mixin.rngevents;

import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @Inject(method = "performShooting", at = @At("HEAD"))
    private void onPerformShooting(CallbackInfo ci, @Local(argsOnly = true, name = "level") Level level, @Local(argsOnly = true, name = "weapon") ItemStack weapon) {
        if (level.isClientSide()) {
            PlayerRandCracker.onCrossbowUse(weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).items().size());
        }
    }
}
