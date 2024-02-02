package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AxeItem.class, HoeItem.class, ShovelItem.class})
public class MixinAxeHoeAndShovelItem {

    @Inject(method = "useOn", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;isClientSide:Z"))
    public void onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> ci) {
        PlayerRandCracker.onItemDamage(1, context.getPlayer(), context.getItemInHand());
    }

}
