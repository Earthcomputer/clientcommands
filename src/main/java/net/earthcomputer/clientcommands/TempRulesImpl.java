package net.earthcomputer.clientcommands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

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
				}
			}
		});
	}

}
