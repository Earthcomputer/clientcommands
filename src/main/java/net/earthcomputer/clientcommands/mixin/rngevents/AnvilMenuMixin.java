package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    public void onAnvilUse(Player player, ItemStack stack, CallbackInfo ci) {
        if (!player.getAbilities().instabuild) {
            PlayerRandCracker.onAnvilUse();
        }
    }

}
