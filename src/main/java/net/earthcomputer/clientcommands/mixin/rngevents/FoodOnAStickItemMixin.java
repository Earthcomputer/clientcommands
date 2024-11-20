package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoodOnAStickItem.class)
public class FoodOnAStickItemMixin {

    @Shadow @Final private int consumeItemDamage;

    @Inject(method = "use", at = @At("HEAD"))
    public void onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        if (player.isPassenger() && player.getVehicle() instanceof Pig) {
            PlayerRandCracker.onItemDamage(consumeItemDamage, player, player.getItemInHand(hand));
        }
    }

}
