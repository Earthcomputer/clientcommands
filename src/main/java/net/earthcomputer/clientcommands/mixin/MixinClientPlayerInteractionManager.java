package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void onRightClickBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> ci) {
        BlockPos pos = hitResult.getBlockPos();
        if (player.world.getBlockState(pos).getBlock() == Blocks.ENCHANTING_TABLE) {
            EnchantmentCracker.enchantingTablePos = pos;
        }
    }

    @Inject(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    public void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        World world = MinecraftClient.getInstance().world;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ItemStack stack = player.getMainHandStack();
        Item item = stack.getItem();
        if (item instanceof MiningToolItem) {
            BlockState state = world.getBlockState(pos);
            if (state.getHardness(world, pos) != 0) {
                PlayerRandCracker.onItemDamage(1, player, stack);
            }
        }
    }

    @Inject(method = "sendSequencedPacket", at = @At("HEAD"))
    private void preSendSequencedPacket(CallbackInfo ci) {
        PlayerRandCracker.isPredictingBlockBreaking = true;
    }

    @Inject(method = "sendSequencedPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V", shift = At.Shift.AFTER))
    private void postSendSequencedPacket(CallbackInfo ci) {
        PlayerRandCracker.postSendBlockBreakingPredictionPacket();
    }

    @Inject(method = "sendSequencedPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/PendingUpdateManager;close()V"))
    private void sendSequencedPacketFinally(CallbackInfo ci) {
        PlayerRandCracker.isPredictingBlockBreaking = false;
    }
}
