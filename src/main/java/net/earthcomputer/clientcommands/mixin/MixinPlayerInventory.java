package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class MixinPlayerInventory {

    @Shadow @Final public DefaultedList<ItemStack> main;
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "offer", at = @At("HEAD"))
    public void onOfferOrDrop(ItemStack stack, boolean notifiesClient, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) {
            int stackSize = stack.getCount();
            for (ItemStack item : main) {
                if (item.isEmpty()) {
                    stackSize -= stack.getMaxCount();
                } else if (item.isItemEqual(stack)) {
                    stackSize -= stack.getMaxCount() - item.getCount();
                }
            }
            if (stackSize > 0) {
                PlayerRandCracker.onDropItem();
            }
        }
    }

}
