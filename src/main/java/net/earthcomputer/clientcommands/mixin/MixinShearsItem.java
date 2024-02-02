package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShearsItem.class)
public class MixinShearsItem {

    @Inject(method = "mineBlock", at = @At("HEAD"))
    public void onPostMine(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity miner, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onItemDamage(1, miner, stack);
    }

}
