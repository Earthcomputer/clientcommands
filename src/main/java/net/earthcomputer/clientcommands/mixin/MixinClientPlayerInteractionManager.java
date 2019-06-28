package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.EnchantmentCracker;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void onRightClickBlock(ClientPlayerEntity player, ClientWorld world, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> ci) {
        BlockPos pos = hitResult.getBlockPos();
        if (world.getBlockState(pos).getBlock() == Blocks.ENCHANTING_TABLE)
            EnchantmentCracker.enchantingTablePos = pos;
    }

}
