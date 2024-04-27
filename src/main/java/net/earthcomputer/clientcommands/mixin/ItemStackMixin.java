package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"))
    public void onHurtAndBreak(int amount, LivingEntity holder, EquipmentSlot slot, CallbackInfo ci) {
        PlayerRandCracker.onItemDamage(amount, holder, (ItemStack) (Object) this);
    }

    @Inject(method = "mineBlock", at = @At("HEAD"))
    private void onMineBlock(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo ci) {
        Tool tool = ((ItemStack) (Object) this).get(DataComponents.TOOL);
        if (tool != null) {
            if (state.getDestroySpeed(level, pos) != 0) {
                PlayerRandCracker.onItemDamage(tool.damagePerBlock(), player, (ItemStack) (Object) this);
            }
        }
    }

}
