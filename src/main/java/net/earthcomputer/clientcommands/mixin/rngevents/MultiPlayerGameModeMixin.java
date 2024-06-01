package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.VillagerRNGSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        Level level = player.level();
        ItemStack stack = player.getMainHandItem();
        Item item = stack.getItem();
        if (item instanceof DiggerItem) {
            BlockState state = level.getBlockState(pos);
            if (state.getDestroySpeed(level, pos) != 0) {
                PlayerRandCracker.onItemDamage(1, player, stack);
            }
        }
    }

    @Inject(method = "startPrediction", at = @At("HEAD"))
    private void preStartPrediction(CallbackInfo ci) {
        PlayerRandCracker.isPredictingBlockBreaking = true;
    }

    @Inject(method = "startPrediction", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER))
    private void postStartPrediction(CallbackInfo ci) {
        PlayerRandCracker.postSendBlockBreakingPredictionPacket();
    }

    @Inject(method = "startPrediction", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/prediction/BlockStatePredictionHandler;close()V"))
    private void startPredictionFinally(CallbackInfo ci) {
        PlayerRandCracker.isPredictingBlockBreaking = false;
    }

}
