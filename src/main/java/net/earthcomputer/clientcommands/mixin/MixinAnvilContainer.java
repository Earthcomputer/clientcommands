package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public class MixinAnvilContainer {

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    public void onAnvilUse(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getAbilities().creativeMode) {
            PlayerRandCracker.onAnvilUse();
        }
    }

}
