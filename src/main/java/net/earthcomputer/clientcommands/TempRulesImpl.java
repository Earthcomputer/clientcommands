package net.earthcomputer.clientcommands;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.ClickType;

public class TempRulesImpl {

	private TempRulesImpl() {
	}

	public static void registerEvents() {
		initBlockReachDistance();
		initToolBreakProtection();
	}

	private static void initBlockReachDistance() {
		TempRules.BLOCK_REACH_DISTANCE.addValueChangeListener(e -> {
			Minecraft.getMinecraft().player.getAttributeMap().getAttributeInstance(EntityPlayer.REACH_DISTANCE)
					.setBaseValue(e.getNewValue());
		});
	}

	private static AtomicInteger hotbarSlotToUpdate = new AtomicInteger(-1);

	private static void initToolBreakProtection() {
		EventManager.addPlayerTickListener(e -> {
			int hotbarSlot = hotbarSlotToUpdate.getAndSet(-1);
			if (hotbarSlot != -1) {
				if (e.player.openContainer == e.player.inventoryContainer) {
					// Pickup the item and put it back again to refresh durability
					for (int i = 0; i < 2; i++) {
						Minecraft.getMinecraft().playerController.windowClick(e.player.openContainer.windowId,
								hotbarSlot, 0, ClickType.PICKUP, e.player);
					}
				}
			}
		});
		EventManager.addPreDamageItemListener(e -> {
			if (TempRules.TOOL_BREAK_PROTECTION.getValue()) {
				if (e.getItemStack().getItemDamage() + e.getDamageAmount() > e.getItemStack().getMaxDamage()) {
					e.setCanceled(true);
					Minecraft.getMinecraft().ingameGUI
							.setOverlayMessage(I18n.format("tempRules.toolBreakProtection.protected"), false);
				}
			}
		});
		EventManager.addPostDamageItemListener(e -> {
			if (TempRules.TOOL_BREAK_PROTECTION.getValue()) {
				// fix client-server desync
				e.getItemStack().setItemDamage(e.getItemStack().getItemDamage() + e.getDamageAmount());
				if (EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, e.getItemStack()) > 0) {
					hotbarSlotToUpdate.set(e.getEntityPlayer().inventory.currentItem);
				}
			}
		});
	}

}
