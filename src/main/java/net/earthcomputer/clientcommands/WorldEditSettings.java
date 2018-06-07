package net.earthcomputer.clientcommands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

public class WorldEditSettings {

	private WorldEditSettings() {
	}

	private static BlockPos selectFrom;
	private static BlockPos selectTo;

	public static void registerEvents() {
		EventManager.addRenderWorldLastListener(e -> {
			GlStateManager.enableBlend();
			GlStateManager.disableDepth();
			GlStateManager.disableCull();
			GlStateManager.disableTexture2D();
			GlStateManager.pushMatrix();
			Entity viewEntity = Minecraft.getMinecraft().getRenderViewEntity();
			GlStateManager.translate(
					-(viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * e.getPartialTicks()),
					-(viewEntity.prevPosY + (viewEntity.posY - viewEntity.prevPosY) * e.getPartialTicks()),
					-(viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * e.getPartialTicks()));
			if (selectFrom != null && selectTo != null) {
				AxisAlignedBB aabb = new AxisAlignedBB(selectFrom, selectTo);
				aabb = new AxisAlignedBB(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX + 1, aabb.maxY + 1, aabb.maxZ + 1);
				RenderGlobal.drawSelectionBoundingBox(aabb, 1, 1, 1, 0.5f);
				RenderGlobal.renderFilledBox(aabb, 1, 1, 1, 0.3f);
			}
			if (selectFrom != null) {
				RenderGlobal.drawBoundingBox(selectFrom.getX(), selectFrom.getY(), selectFrom.getZ(),
						selectFrom.getX() + 1, selectFrom.getY() + 1, selectFrom.getZ() + 1, 0, 1, 0, 0.5f);
			}
			if (selectTo != null) {
				RenderGlobal.drawBoundingBox(selectTo.getX(), selectTo.getY(), selectTo.getZ(), selectTo.getX() + 1,
						selectTo.getY() + 1, selectTo.getZ() + 1, 1, 0, 1, 0.5f);
			}
			GlStateManager.popMatrix();
			GlStateManager.enableTexture2D();
			GlStateManager.enableCull();
			GlStateManager.enableDepth();
			GlStateManager.disableBlend();
		});
	}

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
