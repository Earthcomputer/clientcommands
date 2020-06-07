package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.GuiCloseC2SPacket;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiCloseC2SPacket.class)
public class MixinGuiClosePacket {

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    public void onCreate(int syncId, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        PlayerInventory playerInv = player.inventory;
        if (!playerInv.getCursorStack().isEmpty())
            PlayerRandCracker.onDropItem();

        ScreenHandler container = player.currentScreenHandler;
        if (container instanceof IDroppableInventoryContainer) {
            int[] itemCounts = playerInv.main.stream().mapToInt(ItemStack::getCount).toArray();
            ItemStack[] itemStacks = playerInv.main.toArray(new ItemStack[0]);
            Inventory toDrop = ((IDroppableInventoryContainer) container).getDroppableInventory();
            for (int fromSlot = 0; fromSlot < toDrop.size(); fromSlot++) {
                ItemStack stack = toDrop.getStack(fromSlot);
                int stackSize = stack.getCount();
                for (int toSlot = 0; toSlot < itemCounts.length && stackSize > 0; toSlot++) {
                    if (itemCounts[toSlot] == 0) {
                        itemCounts[toSlot] = Math.min(stackSize, stack.getMaxCount());
                        itemStacks[toSlot] = stack;
                        stackSize -= stack.getMaxCount();
                    } else if (itemStacks[toSlot].isItemEqual(stack)) {
                        stackSize -= stack.getMaxCount() - itemCounts[toSlot];
                        itemCounts[toSlot] = Math.min(itemCounts[toSlot] + stackSize, stack.getMaxCount());
                    }
                }
                if (stackSize > 0) {
                    PlayerRandCracker.onDropItem();
                }
            }
        }

        if (container instanceof BeaconScreenHandler) {
            Slot paymentSlot = container.getSlot(0);
            if (paymentSlot.getStack().getCount() > paymentSlot.getMaxStackAmount())
                PlayerRandCracker.onDropItem();

        }
    }

}
