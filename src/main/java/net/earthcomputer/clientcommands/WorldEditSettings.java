package net.earthcomputer.clientcommands;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

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

}
