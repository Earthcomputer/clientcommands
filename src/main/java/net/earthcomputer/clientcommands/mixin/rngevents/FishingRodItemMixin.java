package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    public void onRetrieveFishingRod(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        ItemStack stack = player.getItemInHand(hand);
        PlayerRandCracker.onItemDamageUncertain(1, 5, player, stack);
    }
}
