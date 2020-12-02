package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingRodItem.class)
public class MixinFishingRodItem {

    @Inject(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/sound/SoundEvents;ENTITY_FISHING_BOBBER_RETRIEVE:Lnet/minecraft/sound/SoundEvent;"))
    public void onRetrieveFishingRod(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> ci) {
        PlayerRandCracker.onItemDamageUncertain(1, 5, player, player.getStackInHand(hand));
    }

    @Inject(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/sound/SoundEvents;ENTITY_FISHING_BOBBER_THROW:Lnet/minecraft/sound/SoundEvent;"))
    private void onThrowFishingRod(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> ci) {
        if (FishingCracker.canManipulateFishing()) {
            FishingCracker.onThrownFishingRod(user.getStackInHand(hand));
        }
    }

}
