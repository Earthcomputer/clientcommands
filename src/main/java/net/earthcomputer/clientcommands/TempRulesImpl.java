package net.earthcomputer.clientcommands;

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

	private static void initToolBreakProtection() {
		EventManager.addPreDamageItemListener(e -> {
			if (TempRules.TOOL_BREAK_PROTECTION.getValue()) {
				if (e.getItemStack().getItemDamage() + e.getDamageAmount() > e.getItemStack().getMaxDamage()) {
					e.setCanceled(true);
					Minecraft.getMinecraft().ingameGUI
							.setOverlayMessage(I18n.format("tempRules.toolBreakProtection.protected"), false);
				} else {
					// Fix client-server desync
					e.getItemStack().setItemDamage(e.getItemStack().getItemDamage() + e.getDamageAmount());
					if (EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, e.getItemStack()) > 0) {
						if (e.getEntityPlayer().openContainer == e.getEntityPlayer().inventoryContainer) {
							// Pickup the item and put it back again to refresh durability
							for (int i = 0; i < 2; i++) {
								Minecraft.getMinecraft().playerController.windowClick(
										e.getEntityPlayer().openContainer.windowId,
										e.getEntityPlayer().inventory.currentItem, 0, ClickType.PICKUP,
										e.getEntityPlayer());
							}
						}
					}
				}
			}
		});
	}

}
