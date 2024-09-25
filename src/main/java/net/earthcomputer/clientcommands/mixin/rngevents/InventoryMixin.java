package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class InventoryMixin {

    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow @Final public Player player;

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"))
    public void onOfferOrDrop(ItemStack stack, boolean notifiesClient, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer)) {
            int stackSize = stack.getCount();
            for (ItemStack item : items) {
                if (item.isEmpty()) {
                    stackSize -= stack.getMaxStackSize();
                } else if (ItemStack.isSameItemSameComponents(item, stack)) {
                    stackSize -= stack.getMaxStackSize() - item.getCount();
                }
            }
            if (stackSize > 0) {
                PlayerRandCracker.onDropItem();
            }
        }
    }

}
