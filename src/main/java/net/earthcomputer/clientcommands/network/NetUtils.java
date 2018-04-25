package net.earthcomputer.clientcommands.network;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketClickWindow;

public class NetUtils {

	/**
	 * Sends a packet to the server
	 */
	public static void sendPacket(Packet<?> packet) {
		Minecraft.getMinecraft().getConnection().sendPacket(packet);
	}

	/**
	 * Resyncs the inventory. Effects are not immediate. Returns whether successful
	 */
	public static boolean resyncInventory(Predicate<Slot> isDesynced) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		Container container = player.openContainer;

		for (Slot slot : container.inventorySlots) {
			if (!slot.getHasStack() && !isDesynced.test(slot)) {
				short transactionId = container.getNextTransactionID(player.inventory);
				sendPacket(new CPacketClickWindow(container.windowId, slot.slotNumber, 0, ClickType.PICKUP,
						new ItemStack(Blocks.COMMAND_BLOCK, 65), transactionId));
				return true;
			}
		}

		return false;
	}

	/**
	 * Sends a message in chat to the server
	 */
	public static void sendChatMessage(String message) {
		sendPacket(new CPacketChatMessage(message));
	}

}
