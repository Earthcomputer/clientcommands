package net.earthcomputer.clientcommands.mixin.commands.enchant;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    public void onRightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> ci) {
        BlockPos pos = hitResult.getBlockPos();
        if (player.level().getBlockState(pos).getBlock() == Blocks.ENCHANTING_TABLE) {
            EnchantmentCracker.enchantingTablePos = pos;
        }
    }
}
