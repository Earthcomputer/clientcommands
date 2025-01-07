package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShearsItem.class)
public class ShearsItemMixin {

    @Inject(method = "mineBlock", at = @At("HEAD"))
    public void onMineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onItemDamage(1, miner, stack);
    }

}
