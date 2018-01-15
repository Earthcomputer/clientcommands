package net.earthcomputer.clientcommands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Mostly a simplified copy of the enchantment table GUI code, but also does the
 * enchanting prediction
 */
public class GuiBetterEnchantment extends GuiContainer {

	// Normal GUI fields
	private static final ResourceLocation ENCHANTMENT_TABLE_GUI_TEXTURE = new ResourceLocation(
			"textures/gui/container/enchanting_table.png");
	private final InventoryPlayer playerInventory;
	private final ContainerBetterEnchantment container;
	private final IWorldNameable nameable;
	private BlockPos position;

	// The default values of the enchantment table container
	private static final int[] NULL_CLUE = { -1, -1, -1 };
	private static final int[] NULL_LEVELS = { 0, 0, 0 };

	// Working values of the brute-force xp seed search
	private static Set<Integer> possibleEnchantmentSeeds = new HashSet<>(1 << 20);
	private static boolean hasReceivedXpSeed = false;
	private static int lastReportedXpSeed;

	static {
		EventManager.addDisconnectListener(e -> {
			// reset working values
			possibleEnchantmentSeeds.clear();
			hasReceivedXpSeed = false;
		});

		EventManager.addGuiOpenListener(e -> {
			// replace default GUI with this GUI if necessary
			if (!ClientCommandsMod.INSTANCE.getTempRules().getBoolean("enchantingPrediction")) {
				return;
			}
			if (e.getGui() instanceof GuiEnchantment) {
				Minecraft mc = Minecraft.getMinecraft();
				IWorldNameable nameable = ReflectionHelper.getPrivateValue(GuiEnchantment.class,
						(GuiEnchantment) e.getGui(), "nameable", "field_175380_I");
				e.setGui(new GuiBetterEnchantment(mc.player.inventory, mc.world, nameable));
			}
		});
	}

	public GuiBetterEnchantment(InventoryPlayer inventory, World worldIn, IWorldNameable nameable) {
		super(new ContainerBetterEnchantment(inventory, worldIn));
		this.playerInventory = inventory;
		this.container = (ContainerBetterEnchantment) inventorySlots;
		container.gui = this;
		this.nameable = nameable;
		RayTraceResult rayTraceResult = Minecraft.getMinecraft().objectMouseOver;
		if (rayTraceResult.typeOfHit == RayTraceResult.Type.BLOCK) {
			this.position = rayTraceResult.getBlockPos();
		}
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		fontRenderer.drawString(nameable.getDisplayName().getUnformattedText() + " (Better GUI)", 12, 5, 0x404040);
		fontRenderer.drawString(playerInventory.getDisplayName().getUnformattedText(), 8, ySize - 96 + 2, 0x404040);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		// check for if an enchant button has been pressed
		int leftX = (width - xSize) / 2;
		int topY = (height - ySize) / 2;

		for (int slot = 0; slot < 3; slot++) {
			int slotsLeft = mouseX - (leftX + 60);
			int slotTop = mouseY - (topY + 14 + 19 * slot);

			if (slotsLeft >= 0 && slotTop >= 0 && slotsLeft < 108 && slotTop < 19
					&& container.enchantItem(mc.player, slot)) {
				mc.playerController.sendEnchantPacket(container.windowId, slot);
			}
		}
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		// extra info in top left
		if (possibleEnchantmentSeeds.size() == 1) {
			fontRenderer.drawString(String.format("Enchantment seed: %08X", possibleEnchantmentSeeds.iterator().next()),
					0, 0, 0xffffff);
		} else {
			fontRenderer.drawString(String.format("Possible enchantment seeds: %d", possibleEnchantmentSeeds.size()), 0,
					0, 0xffffff);
		}

		// background
		mc.getTextureManager().bindTexture(ENCHANTMENT_TABLE_GUI_TEXTURE);
		int leftX = (width - xSize) / 2;
		int topY = (height - ySize) / 2;
		drawTexturedModalRect(leftX, topY, 0, 0, xSize, ySize);

		// draw slots
		int lapis = container.getLapisAmount();
		for (int slot = 0; slot < 3; slot++) {
			int slotsLeft = leftX + 60;
			int textLeft = slotsLeft + 20;
			zLevel = 0;
			mc.getTextureManager().bindTexture(ENCHANTMENT_TABLE_GUI_TEXTURE);
			int levels = container.enchantLevels[slot];
			GlStateManager.color(1, 1, 1, 1);

			if (levels == 0) {
				drawTexturedModalRect(slotsLeft, topY + 14 + 19 * slot, 0, 185, 108, 19);
			} else {
				String strLevels = String.valueOf(levels);
				int wrapWidth = 86 - fontRenderer.getStringWidth(strLevels);
				Enchantment clueEnch = Enchantment.getEnchantmentByID(container.enchantClue[slot]);
				String clueLvl = I18n.format("container.enchant.clue",
						clueEnch == null ? "" : clueEnch.getTranslatedName(container.worldClue[slot]));

				int levelsColor = 0x685e4a;

				if ((lapis < slot + 1 || mc.player.experienceLevel < levels)
						&& !mc.player.capabilities.isCreativeMode) {
					// the player cannot enchant with this slot, gray it out
					drawTexturedModalRect(slotsLeft, topY + 14 + 19 * slot, 0, 185, 108, 19);
					drawTexturedModalRect(slotsLeft + 1, topY + 15 + 19 * slot, 16 * slot, 239, 16, 16);
					fontRenderer.drawSplitString(clueLvl, textLeft, topY + 16 + 19 * slot, wrapWidth,
							(levelsColor & 0xfefefe) >> 1);
					levelsColor = 0x407f10;
				} else {
					int relativeMouseX = mouseX - (leftX + 60);
					int relativeMouseY = mouseY - (topY + 14 + 19 * slot);

					if (relativeMouseX >= 0 && relativeMouseY >= 0 && relativeMouseX < 108 && relativeMouseY < 19) {
						// the slot is being hovered over, highlight it
						drawTexturedModalRect(slotsLeft, topY + 14 + 19 * slot, 0, 204, 108, 19);
						levelsColor = 0xffff80;
					} else {
						// draw the slot normally
						drawTexturedModalRect(slotsLeft, topY + 14 + 19 * slot, 0, 166, 108, 19);
					}

					drawTexturedModalRect(slotsLeft + 1, topY + 15 + 19 * slot, 16 * slot, 223, 16, 16);
					fontRenderer.drawSplitString(clueLvl, textLeft, topY + 16 + 19 * slot, wrapWidth, levelsColor);
					levelsColor = 0x80ff20;
				}

				mc.fontRenderer.drawStringWithShadow(strLevels,
						textLeft + 86 - mc.fontRenderer.getStringWidth(strLevels), topY + 16 + 19 * slot + 7,
						levelsColor);
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		partialTicks = mc.getTickLength();
		drawDefaultBackground();
		super.drawScreen(mouseX, mouseY, partialTicks);
		renderHoveredToolTip(mouseX, mouseY);

		// draw the tooltip over the slot if necessary

		boolean creativeMode = mc.player.capabilities.isCreativeMode;
		int lapis = container.getLapisAmount();

		for (int slot = 0; slot < 3; slot++) {
			int levels = container.enchantLevels[slot];
			Enchantment clueEnch = Enchantment.getEnchantmentByID(container.enchantClue[slot]);
			int clueLvl = container.worldClue[slot];
			int lapisRequired = slot + 1;

			if (isPointInRegion(60, 14 + 19 * slot, 108, 17, mouseX, mouseY) && levels > 0 && clueLvl >= 0
					&& clueEnch != null) {
				List<String> lines = new ArrayList<>();

				if (possibleEnchantmentSeeds.size() == 1) {
					// we have our enchantment seed, get the exact enchantments
					List<EnchantmentData> enchantmentList = getEnchantmentList(new Random(),
							possibleEnchantmentSeeds.iterator().next(), container.tableInventory.getStackInSlot(0),
							slot, container.enchantLevels[slot]);
					for (EnchantmentData ench : enchantmentList) {
						lines.add("" + TextFormatting.WHITE + TextFormatting.ITALIC
								+ ench.enchantment.getTranslatedName(ench.enchantmentLevel));
					}
				} else {
					// only give the default clue
					lines.add("" + TextFormatting.WHITE + TextFormatting.ITALIC
							+ I18n.format("container.enchant.clue", clueEnch.getTranslatedName(clueLvl)));
				}

				if (!creativeMode) {
					lines.add("");

					if (mc.player.experienceLevel < levels) {
						lines.add(TextFormatting.RED + "Level Requirement: " + container.enchantLevels[slot]);
					} else {
						String str;

						if (lapisRequired == 1) {
							str = I18n.format("container.enchant.lapis.one", new Object[0]);
						} else {
							str = I18n.format("container.enchant.lapis.many",
									new Object[] { Integer.valueOf(lapisRequired) });
						}

						TextFormatting color = lapis >= lapisRequired ? TextFormatting.GRAY : TextFormatting.RED;
						lines.add(color + str);

						if (lapisRequired == 1) {
							str = I18n.format("container.enchant.level.one", new Object[0]);
						} else {
							str = I18n.format("container.enchant.level.many",
									new Object[] { Integer.valueOf(lapisRequired) });
						}

						lines.add(TextFormatting.GRAY + "" + str);
					}
				}

				drawHoveringText(lines, mouseX, mouseY);
				break;
			}
		}
	}

	private void recalcXpSeed() {
		Set<Integer> possibleSeeds = possibleEnchantmentSeeds;
		if (!hasReceivedXpSeed || lastReportedXpSeed != container.xpSeed) {
			// re-initialize everything
			hasReceivedXpSeed = true;
			lastReportedXpSeed = container.xpSeed;
			possibleSeeds.clear();
			int reportedSeed = container.xpSeed & 0x0000fff0;
			for (int highBits = 0; highBits <= 0xffff; highBits++) {
				for (int lowBits = 0; lowBits <= 0xf; lowBits++) {
					possibleSeeds.add(reportedSeed | (highBits << 16) | lowBits);
				}
			}
		}

		if (Arrays.equals(container.enchantLevels, NULL_LEVELS) && Arrays.equals(container.enchantClue, NULL_CLUE)
				&& Arrays.equals(container.worldClue, NULL_CLUE)) {
			// Prevent eliminating results with the wrong data
			return;
		}

		// Slightly copied from ContainerEnchantment, in order to simulate the
		// enchantments
		ItemStack itemToEnchant = container.tableInventory.getStackInSlot(0);
		if (itemToEnchant.isEmpty() || !itemToEnchant.isItemEnchantable()) {
			return;
		}

		float power = 0;

		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if ((dz != 0 || dx != 0) && mc.world.isAirBlock(position.add(dx, 0, dz))
						&& mc.world.isAirBlock(position.add(dx, 1, dz))) {
					power += ForgeHooks.getEnchantPower(mc.world, position.add(dx * 2, 0, dz * 2));
					power += ForgeHooks.getEnchantPower(mc.world, position.add(dx * 2, 1, dz * 2));
					if (dx != 0 && dz != 0) {
						power += ForgeHooks.getEnchantPower(mc.world, position.add(dx * 2, 0, dz));
						power += ForgeHooks.getEnchantPower(mc.world, position.add(dx * 2, 1, dz));
						power += ForgeHooks.getEnchantPower(mc.world, position.add(dx, 0, dz * 2));
						power += ForgeHooks.getEnchantPower(mc.world, position.add(dx, 1, dz * 2));
					}
				}
			}
		}
		Random rand = new Random();

		int[] enchantLevels = new int[3];
		int[] enchantClue = new int[3];
		int[] worldClue = new int[3];

		Iterator<Integer> seedItr = possibleSeeds.iterator();
		while (seedItr.hasNext()) {
			int xpSeed = seedItr.next();
			rand.setSeed(xpSeed);

			for (int slot = 0; slot < 3; slot++) {
				enchantLevels[slot] = EnchantmentHelper.calcItemStackEnchantability(rand, slot, (int) power,
						itemToEnchant);
				enchantClue[slot] = -1;
				worldClue[slot] = -1;

				if (enchantLevels[slot] < slot + 1) {
					enchantLevels[slot] = 0;
				}
				enchantLevels[slot] = ForgeEventFactory.onEnchantmentLevelSet(mc.world, position, slot, (int) power,
						itemToEnchant, enchantLevels[slot]);
			}

			for (int slot = 0; slot < 3; slot++) {
				if (enchantLevels[slot] > 0) {
					List<EnchantmentData> enchantments = getEnchantmentList(rand, xpSeed, itemToEnchant, slot,
							enchantLevels[slot]);

					if (enchantments != null && !enchantments.isEmpty()) {
						EnchantmentData enchantmentdata = enchantments.get(rand.nextInt(enchantments.size()));
						enchantClue[slot] = Enchantment.getEnchantmentID(enchantmentdata.enchantment);
						worldClue[slot] = enchantmentdata.enchantmentLevel;
					}
				}
			}

			if (!Arrays.equals(enchantLevels, container.enchantLevels)
					|| !Arrays.equals(enchantClue, container.enchantClue)
					|| !Arrays.equals(worldClue, container.worldClue)) {
				seedItr.remove();
			}
		}

	}

	/**
	 * Gets the list of enchantments that would go on a certain item stack, given
	 * the xp seed
	 */
	private List<EnchantmentData> getEnchantmentList(Random rand, int xpSeed, ItemStack stack, int enchantSlot,
			int level) {
		rand.setSeed(xpSeed + enchantSlot);
		List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(rand, stack, level, false);

		if (stack.getItem() == Items.BOOK && list.size() > 1) {
			list.remove(rand.nextInt(list.size()));
		}

		return list;
	}

	private static class ContainerBetterEnchantment extends ContainerEnchantment {

		public GuiBetterEnchantment gui;

		public ContainerBetterEnchantment(InventoryPlayer playerInv, World worldIn) {
			super(playerInv, worldIn);
		}

		@Override
		public void updateProgressBar(int id, int data) {
			super.updateProgressBar(id, data);
			if (id == 9) {
				gui.recalcXpSeed();
			}
		}

	}

}
