package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerboundContainerClosePacket.class)
public class ServerboundContainerClosePacketMixin {

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    public void onCreate(int syncId, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;

        Inventory playerInv = player.getInventory();
        AbstractContainerMenu menu = player.containerMenu;

        if (!menu.getCarried().isEmpty()) {
            PlayerRandCracker.onDropItem();
        }

        if (menu instanceof IDroppableInventoryContainer) {
            int[] itemCounts = playerInv.items.stream().mapToInt(ItemStack::getCount).toArray();
            ItemStack[] itemStacks = playerInv.items.toArray(new ItemStack[0]);
            Container toDrop = ((IDroppableInventoryContainer) menu).getDroppableInventory();
            for (int fromSlot = 0; fromSlot < toDrop.getContainerSize(); fromSlot++) {
                ItemStack stack = toDrop.getItem(fromSlot);
                int stackSize = stack.getCount();
                for (int toSlot = 0; toSlot < itemCounts.length && stackSize > 0; toSlot++) {
                    if (itemCounts[toSlot] == 0) {
                        itemCounts[toSlot] = Math.min(stackSize, stack.getMaxStackSize());
                        itemStacks[toSlot] = stack;
                        stackSize -= stack.getMaxStackSize();
                    } else if (ItemStack.isSameItemSameComponents(itemStacks[toSlot], stack)) {
                        stackSize -= stack.getMaxStackSize() - itemCounts[toSlot];
                        itemCounts[toSlot] = Math.min(itemCounts[toSlot] + stackSize, stack.getMaxStackSize());
                    }
                }
                if (stackSize > 0) {
                    PlayerRandCracker.onDropItem();
                }
            }
        }

        if (menu instanceof BeaconMenu) {
            Slot paymentSlot = menu.getSlot(0);
            if (paymentSlot.getItem().getCount() > paymentSlot.getMaxStackSize()) {
                PlayerRandCracker.onDropItem();
            }
        }
    }

}
