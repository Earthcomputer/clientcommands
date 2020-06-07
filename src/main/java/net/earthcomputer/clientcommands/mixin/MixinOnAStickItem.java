package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.OnAStickItem;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OnAStickItem.class)
public class MixinOnAStickItem {

    @Shadow @Final private int damagePerUse;

    @Inject(method = "use", at = @At("HEAD"))
    public void onUse(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> ci) {
        if (player.hasVehicle() && player.getVehicle() instanceof PigEntity) {
            PlayerRandCracker.onItemDamage(damagePerUse, player, player.getStackInHand(hand));
        }
    }

}
