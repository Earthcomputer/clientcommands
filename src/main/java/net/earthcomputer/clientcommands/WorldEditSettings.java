package net.earthcomputer.clientcommands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

public class WorldEditSettings {

	private WorldEditSettings() {
	}

	private static BlockPos selectFrom;
	private static BlockPos selectTo;

	public static boolean hasSelection() {
		return selectFrom != null && selectTo != null;
	}

	public static void deselect() {
		selectFrom = selectTo = null;
		Minecraft.getMinecraft().ingameGUI.getChatGUI()
				.printChatMessage(new TextComponentTranslation("commands.cselect.deselect.success"));
	}

	public static BlockPos getSelectFrom() {
		return selectFrom;
	}

	public static void setSelectFrom(BlockPos selectFrom) {
		WorldEditSettings.selectFrom = selectFrom;
		Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(
				"commands.cselect.start.success", selectFrom.getX(), selectFrom.getY(), selectFrom.getZ()));
	}

	public static BlockPos getSelectTo() {
		return selectTo;
	}

	public static void setSelectTo(BlockPos selectTo) {
		WorldEditSettings.selectTo = selectTo;
		Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(
				"commands.cselect.end.success", selectTo.getX(), selectTo.getY(), selectTo.getZ()));
	}

	public static boolean isWand(ItemStack stack) {
		return stack.getItem() == Items.WOODEN_SWORD
				&& stack.hasTagCompound()
				&& stack.getTagCompound().hasKey("clientcommandstool", Constants.NBT.TAG_STRING)
				&& stack.getTagCompound().getString("clientcommandstool").equals("wand");
	}

	public static ItemStack createWand() {
		ItemStack wand = new ItemStack(Items.WOODEN_SWORD);
		wand.setStackDisplayName(TextFormatting.GOLD + I18n.format("item.clientcommandsWand.name"));
		wand.getTagCompound().setString("clientcommandstool", "wand");
		wand.addEnchantment(Enchantments.EFFICIENCY, 5);
		return wand;
	}

}
