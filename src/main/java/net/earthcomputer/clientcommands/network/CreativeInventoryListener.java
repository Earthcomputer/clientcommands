package net.earthcomputer.clientcommands.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class CreativeInventoryListener implements IContainerListener {

	@Override
	public void sendAllContents(Container container, NonNullList<ItemStack> stacks) {
		PlayerControllerMP playerController = Minecraft.getMinecraft().playerController;
		for (int i = 0; i < stacks.size(); i++) {
			playerController.sendSlotPacket(stacks.get(i), i);
		}
	}

	@Override
	public void sendAllWindowProperties(Container container, IInventory inventory) {
	}

	@Override
	public void sendSlotContents(Container container, int slot, ItemStack stack) {
		Minecraft.getMinecraft().playerController.sendSlotPacket(stack, slot);
	}

	@Override
	public void sendWindowProperty(Container container, int field, int value) {
	}

}
