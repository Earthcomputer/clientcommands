package net.earthcomputer.clientcommands.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Simulates Inventory.placeItemBackInInventory without modifying the real inventory
public final class SimulatedPlayerInventory {
    private static final int OFFHAND_INDEX = Inventory.INVENTORY_SIZE;

    // the main inventory, followed by the offhand. Armor slots aren't used for placing items back
    private final ItemStack[] items = new ItemStack[Inventory.INVENTORY_SIZE + 1];
    private final int selectedSlot;

    public SimulatedPlayerInventory(Inventory inventory) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            items[i] = inventory.getNonEquipmentItems().get(i).copy();
        }
        items[OFFHAND_INDEX] = inventory.getItem(Inventory.SLOT_OFFHAND).copy();
        selectedSlot = inventory.getSelectedSlot();
    }

    // returns false if some of the item would be dropped
    public boolean placeItemBackInInventory(ItemStack stack) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int slot = getSlotWithRemainingSpace(stack);
            if (slot == -1) {
                slot = getFreeSlot();
            }
            if (slot == -1) {
                return false;
            }

            if (items[slot].isEmpty()) {
                int count = Math.min(remaining, stack.getMaxStackSize());
                items[slot] = stack.copyWithCount(count);
                remaining -= count;
            } else {
                int count = Math.min(remaining, stack.getMaxStackSize() - items[slot].getCount());
                items[slot].grow(count);
                remaining -= count;
            }
        }
        return true;
    }

    private int getSlotWithRemainingSpace(ItemStack stack) {
        if (hasRemainingSpaceForItem(items[selectedSlot], stack)) {
            return selectedSlot;
        }
        if (hasRemainingSpaceForItem(items[OFFHAND_INDEX], stack)) {
            return OFFHAND_INDEX;
        }
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (hasRemainingSpaceForItem(items[i], stack)) {
                return i;
            }
        }
        return -1;
    }

    private int getFreeSlot() {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (items[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasRemainingSpaceForItem(ItemStack slotStack, ItemStack stack) {
        return !slotStack.isEmpty()
            && ItemStack.isSameItemSameComponents(slotStack, stack)
            && slotStack.isStackable()
            && slotStack.getCount() < slotStack.getMaxStackSize();
    }
}
