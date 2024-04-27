package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingRodItem.class)
public class FishingRodItemMixin {

    @Inject(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/sounds/SoundEvents;FISHING_BOBBER_RETRIEVE:Lnet/minecraft/sounds/SoundEvent;"))
    public void onRetrieveFishingRod(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (level.isClientSide) {
            ItemStack stack = player.getItemInHand(hand);
            PlayerRandCracker.onItemDamageUncertain(1, 5, player, stack);
            if (FishingCracker.canManipulateFishing()) {
                FishingCracker.onRetractedFishingRod();
            }
        }
    }

    @Inject(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/sounds/SoundEvents;FISHING_BOBBER_THROW:Lnet/minecraft/sounds/SoundEvent;"))
    private void onThrowFishingRod(Level level, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (level.isClientSide && FishingCracker.canManipulateFishing()) {
            FishingCracker.onThrownFishingRod(user.getItemInHand(hand));
        }
    }

}
