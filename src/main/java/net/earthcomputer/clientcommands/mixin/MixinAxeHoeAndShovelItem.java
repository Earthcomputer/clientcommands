package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ShovelItem;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AxeItem.class, HoeItem.class, ShovelItem.class})
public class MixinAxeHoeAndShovelItem {

    @Inject(method = "useOnBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isClient:Z"))
    public void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> ci) {
        PlayerRandCracker.onItemDamage(1, context.getPlayer(), context.getStack());
    }

}
