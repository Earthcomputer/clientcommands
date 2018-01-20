package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.BlockStructure;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketEnchantItem;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketAdvancementInfo;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketWindowProperty;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.event.ForgeEventFactory;

public class EnchantmentCracker {

	private static final Logger LOGGER = LogManager.getLogger(ClientCommandsMod.MODID);

	private static final long MULTIPLIER = 0x5deece66dL;
	private static final long ADDEND = 0xbL;
	private static final long MASK = (1L << 48) - 1;

	private static Set<Integer> possibleXPSeeds = new HashSet<>(1 << 20);
	private static boolean onFirstXPSeed = true;
	private static int windowPropertyUpdatesUntilXPSeedUpdate = -1;
	private static Set<Long> possiblePlayerRandSeeds = new HashSet<>(1 << 16);
	private static Random playerRand = new Random();

	private static boolean wasWet = false;

	public static void registerEvents() {
		TempRules.ENCHANTING_PREDICTION.addValueChangeListener(e -> {
			if (!e.getNewValue()) {
				resetCracker();
			}
		});
		EventManager.addLivingAttackListener(e -> {
			if (e.getEntity() == Minecraft.getMinecraft().player) {
				resetCracker("player hurt");
			}
		});
		EventManager.addPlayerTickListener(e -> {
			EntityPlayer player = e.player;
			if (player.isSprinting()) {
				resetCracker("sprinting");
			}
			if (player.isWet() && !wasWet) {
				resetCracker("entered water");
			}
			if (player.isWet() && (!player.isSneaking() || !player.onGround)) {
				resetCracker("swimming");
			}
			if (!player.getActivePotionEffects().isEmpty()) {
				resetCracker("potion effect active");
			}
			if (!EnchantmentHelper.getEnchantedItem(Enchantments.MENDING, player).isEmpty() && !player.world
					.getEntitiesWithinAABB(EntityXPOrb.class, player.getEntityBoundingBox()).isEmpty()) {
				resetCracker("mending item");
			}
			if (player.isInsideOfMaterial(Material.WATER) && EnchantmentHelper.getRespirationModifier(player) > 0) {
				resetCracker("using respiration");
			}
			if (EnchantmentHelper.hasFrostWalkerEnchantment(player)) {
				frostWalkerCheck(player, EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.FROST_WALKER, player));
			}
			if (player.isElytraFlying()) {
				itemUseCheck(player.inventory.armorInventory.get(2), 1);
			}
			wasWet = player.isWet();
		});
		EventManager.addEntitySpawnListener(e -> {
			if (e.getEntity() instanceof EntityItem && e.getEntity().getEntityBoundingBox()
					.intersects(Minecraft.getMinecraft().player.getEntityBoundingBox())) {
				resetCracker("drop item");
			}
		});
		EventManager.addAnvilRepairListener(e -> {
			resetCracker("anvil use");
		});
		EventManager.addUseBlockListener(e -> {
			ItemStack stack = e.getItemStack();
			if (stack.isEmpty()) {
				return;
			}
			Item item = stack.getItem();
			if (e.getWorld().getBlockState(e.getPos()).getBlock() == Blocks.TNT) {
				if (item == Items.FLINT_AND_STEEL) {
					itemUseCheck(stack, 1);
				}
			}
			if (e.getEntityPlayer().canPlayerEdit(e.getPos().offset(e.getFace()), e.getFace(), stack)) {
				if (item instanceof ItemHoe || item == Items.FLINT_AND_STEEL) {
					itemUseCheck(stack, 1);
				} else if (item instanceof ItemSpade) {
					if (e.getFace() != EnumFacing.DOWN
							&& e.getWorld().getBlockState(e.getPos().up()).getMaterial() == Material.AIR
							&& e.getWorld().getBlockState(e.getPos()).getBlock() == Blocks.GRASS) {
						itemUseCheck(stack, 1);
					}
				}
			}
		});
		EventManager.addAttackBlockListener(e -> {
			if (!Minecraft.getMinecraft().playerController.isInCreativeMode()) {
				IBlockState hitState = e.getWorld().getBlockState(e.getPos());
				if (hitState.getMaterial() != Material.AIR && hitState
						.getPlayerRelativeBlockHardness(e.getEntityPlayer(), e.getWorld(), e.getPos()) >= 1) {
					onDestroyBlock(e.getWorld(), e.getPos(), e.getEntityPlayer());
				}
			}
		});
		EventManager.addUseItemListener(e -> {
			ItemStack stack = e.getItemStack();
			if (stack.isEmpty()) {
				return;
			}
			Item item = stack.getItem();
			if (e.getItemStack().getItemUseAction() == EnumAction.EAT) {
				resetCracker("eating");
			} else if (item == Items.CARROT_ON_A_STICK) {
				itemUseCheck(stack, 7);
			} else if (item instanceof ItemFishingRod) {
				itemUseCheck(stack, 5); // assume 5 (the max in vanilla)
			}
		});
		EventManager.addAttackEntityListener(e -> {
			ItemStack heldStack = e.getEntityPlayer().getHeldItemMainhand();
			if (!heldStack.isEmpty()) {
				if (e.getTarget() instanceof EntityLiving
						&& ((EntityLiving) e.getTarget()).getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD) {
					if (EnchantmentHelper.getEnchantments(heldStack).containsKey(Enchantments.BANE_OF_ARTHROPODS)) {
						resetCracker("bane of arthropods");
					}
				}
				Item heldItem = heldStack.getItem();
				if (heldItem instanceof ItemSword || heldItem instanceof ItemHoe) {
					itemUseCheck(heldStack, 1);
				} else if (heldItem instanceof ItemTool) {
					itemUseCheck(heldStack, 2);
				}
			}
		});
		EventManager.addUseEntityListener(e -> {
			ItemStack usedStack = e.getItemStack();
			if (!usedStack.isEmpty()) {
				if (usedStack.getItem() instanceof ItemShears) {
					if (e.getTarget() instanceof IShearable) {
						if (((IShearable) e.getTarget()).isShearable(usedStack, e.getWorld(),
								new BlockPos(e.getTarget()))) {
							itemUseCheck(usedStack, 1);
						}
					}
				}
				if (usedStack.getItem() == Items.FLINT_AND_STEEL) {
					if (e.getTarget() instanceof EntityCreeper) {
						itemUseCheck(usedStack, 1);
					}
				}
			}
		});
		EventManager.addFireBowListener(e -> {
			if (e.hasAmmo() && ItemBow.getArrowVelocity(e.getCharge()) >= 0.1) {
				itemUseCheck(e.getBow(), 1);
			}
		});
		EventManager.addInboundPacketPreListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof SPacketAdvancementInfo) {
				SPacketAdvancementInfo advancementInfo = (SPacketAdvancementInfo) packet;
				if (!advancementInfo.isFirstSync() && advancementInfo.getProgressUpdates().values().stream()
						.anyMatch(AdvancementProgress::isDone)) {
					resetCracker("gain advancement");
				}
			} else if (packet instanceof SPacketEntityStatus) {
				SPacketEntityStatus entityStatus = (SPacketEntityStatus) packet;
				if (entityStatus.getOpCode() == 29) {
					if (entityStatus.getEntity(Minecraft.getMinecraft().world) == Minecraft.getMinecraft().player) {
						resetCracker("blocking with shield");
					}
				}
			} else if (packet instanceof SPacketWindowProperty) {
				if (TempRules.ENCHANTING_PREDICTION.getValue()) {
					SPacketWindowProperty windowProp = (SPacketWindowProperty) packet;
					Minecraft mc = Minecraft.getMinecraft();
					Container container = mc.player.openContainer;
					if (container instanceof ContainerEnchantment && windowProp.getWindowId() == container.windowId) {
						ContainerEnchantment enchContainer = (ContainerEnchantment) container;
						int currentValue;
						if (windowProp.getProperty() < 3) {
							currentValue = enchContainer.enchantLevels[windowProp.getProperty()];
						} else if (windowProp.getProperty() == 3) {
							currentValue = enchContainer.xpSeed;
						} else if (windowProp.getProperty() < 7) {
							currentValue = enchContainer.enchantClue[windowProp.getProperty() - 4];
						} else {
							currentValue = enchContainer.worldClue[windowProp.getProperty() - 7];
						}
						if (windowProp.getValue() != currentValue) {
							windowPropertyUpdatesUntilXPSeedUpdate = 9;
						} else if (windowPropertyUpdatesUntilXPSeedUpdate >= 0) {
							windowPropertyUpdatesUntilXPSeedUpdate--;
							if (windowPropertyUpdatesUntilXPSeedUpdate == 0) {
								BlockPos tablePos = null;
								if (mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
									if (mc.world.getBlockState(mc.objectMouseOver.getBlockPos())
											.getBlock() == Blocks.ENCHANTING_TABLE) {
										tablePos = mc.objectMouseOver.getBlockPos();
									}
								}
								if (tablePos != null) {
									addEnchantmentSeedInfo(mc.world, tablePos, enchContainer);
								}
							}
						}
					}
				}
			}
		});
		EventManager.addOutboundPacketPreListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof CPacketChatMessage) {
				CPacketChatMessage chat = (CPacketChatMessage) packet;
				String message = chat.getMessage();
				if (message.startsWith("/") && message.substring(1).trim().startsWith("give")) {
					resetCracker("give command");
				}
			} else if (packet instanceof CPacketPlayerDigging) {
				CPacketPlayerDigging digPacket = (CPacketPlayerDigging) packet;
				if (digPacket.getAction() == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
					Minecraft mc = Minecraft.getMinecraft();
					onDestroyBlock(mc.world, digPacket.getPosition(), mc.player);
				}
			} else if (packet instanceof CPacketEnchantItem) {
				onEnchantedItem();
			}
		});
		EventManager.addGuiOverlayListener(e -> {
			if (e.getGui() instanceof GuiEnchantment) {
				drawEnchantmentGUIOverlay();
			}
		});
	}

	public static void resetCracker() {
		TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.UNCRACKED);
		onFirstXPSeed = true;
		possibleXPSeeds.clear();
		possiblePlayerRandSeeds.clear();
	}

	public static void resetCracker(String reason) {
		if (TempRules.ENCHANTING_CRACK_STATE.getValue() != EnumCrackState.UNCRACKED) {
			Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
					new TextComponentString(TextFormatting.RED + "Restarting enchantment cracking. Reason: " + reason));
		}
		resetCracker();
	}

	private static void frostWalkerCheck(EntityPlayer player, int level) {
		World world = player.world;
		BlockPos pos = new BlockPos(player);

		if (player.onGround) {
			float radius = (float) Math.min(16, 2 + level);
			BlockPos.MutableBlockPos posAboveWater = new BlockPos.MutableBlockPos(0, 0, 0);

			for (BlockPos.MutableBlockPos waterPos : BlockPos.getAllInBoxMutable(pos.add(-radius, -1, -radius),
					pos.add(radius, -1, radius))) {
				if (waterPos.distanceSqToCenter(player.posX, player.posY, player.posZ) <= radius * radius) {
					posAboveWater.setPos(waterPos.getX(), waterPos.getY() + 1, waterPos.getZ());
					IBlockState stateAboveWater = world.getBlockState(posAboveWater);

					if (stateAboveWater.getMaterial() == Material.AIR) {
						IBlockState stateAtWater = world.getBlockState(waterPos);

						if (stateAtWater.getBlock() == Blocks.FROSTED_ICE) {
							resetCracker("frost walking");
							return;
						}
					}
				}
			}
		}
	}

	private static void itemUseCheck(ItemStack heldStack, int damageAmount) {
		if (EnchantmentHelper.getEnchantments(heldStack).containsKey(Enchantments.UNBREAKING)) {
			resetCracker("unbreaking item");
		} else if (heldStack.getItemDamage() + damageAmount > heldStack.getMaxDamage() + 1) {
			resetCracker("broke item");
		}
	}

	private static void onDestroyBlock(World world, BlockPos pos, EntityPlayer player) {
		IBlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		ItemStack stack = player.getHeldItemMainhand();
		Minecraft mc = Minecraft.getMinecraft();

		// perform necessary extra checks
		if (mc.playerController.getCurrentGameType().hasLimitedInteractions()) {
			if (mc.playerController.getCurrentGameType() == GameType.SPECTATOR) {
				return;
			}
			if (!player.isAllowEdit()) {
				if (stack.isEmpty()) {
					return;
				}
				if (!stack.canDestroy(block)) {
					return;
				}
			}
		}
		if (block instanceof BlockCommandBlock || block instanceof BlockStructure) {
			return;
		}
		if (state.getMaterial() == Material.AIR) {
			return;
		}

		// perform the item use check on the item if necessary
		if (!stack.isEmpty()) {
			Item item = stack.getItem();
			if (item instanceof ItemShears) {
				itemUseCheck(stack, 1);
			} else if (item instanceof ItemSword || item instanceof ItemTool) {
				if (world.getBlockState(pos).getBlockHardness(world, pos) != 0) {
					itemUseCheck(stack, item instanceof ItemSword ? 1 : 2);
				}
			}
		}
	}

	private static void drawEnchantmentGUIOverlay() {
		if (!TempRules.ENCHANTING_PREDICTION.getValue()) {
			return;
		}

		EnumCrackState crackState = TempRules.ENCHANTING_CRACK_STATE.getValue();

		List<String> lines = new ArrayList<>();

		lines.add("Crack state: " + crackState.getName());

		lines.add("");

		if (crackState == EnumCrackState.CRACKED || crackState == EnumCrackState.CRACKED_ENCH_SEED) {
			lines.add("Enchantments:");
		} else {
			lines.add("Clues:");
		}

		for (int slot = 0; slot < 3; slot++) {
			lines.add("Slot " + (slot + 1) + ":");
			List<EnchantmentData> enchs = getEnchantmentsInTable(slot);
			if (enchs != null) {
				for (EnchantmentData ench : enchs) {
					lines.add("   " + ench.enchantment.getTranslatedName(ench.enchantmentLevel));
				}
			}
		}

		FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
		int y = 0;
		for (String line : lines) {
			fontRenderer.drawString(line, 0, y, 0xffffff);
			y += fontRenderer.FONT_HEIGHT;
		}
	}

	private static void prepareForNextEnchantmentSeedCrack(int serverReportedXPSeed) {
		serverReportedXPSeed &= 0x0000fff0;
		for (int highBits = 0; highBits < 65536; highBits++) {
			for (int low4Bits = 0; low4Bits < 16; low4Bits++) {
				possibleXPSeeds.add((highBits << 16) | serverReportedXPSeed | low4Bits);
			}
		}
	}

	public static void addEnchantmentSeedInfo(World world, BlockPos tablePos, ContainerEnchantment container) {
		EnumCrackState crackState = TempRules.ENCHANTING_CRACK_STATE.getValue();
		if (crackState == EnumCrackState.CRACKED_ENCH_SEED || crackState == EnumCrackState.CRACKED) {
			return;
		}

		ItemStack itemToEnchant = container.tableInventory.getStackInSlot(0);
		if (itemToEnchant.isEmpty() || !itemToEnchant.isItemEnchantable()) {
			return;
		}

		if (crackState == EnumCrackState.UNCRACKED || crackState == EnumCrackState.CRACKING) {
			TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.CRACKING_ENCH_SEED);
			prepareForNextEnchantmentSeedCrack(container.xpSeed);
		}
		int power = getEnchantPower(world, tablePos);

		Random rand = new Random();
		int[] actualEnchantLevels = container.enchantLevels;
		int[] actualEnchantmentClues = container.enchantClue;
		int[] actualLevelClues = container.worldClue;

		Iterator<Integer> xpSeedItr = possibleXPSeeds.iterator();
		seedLoop: while (xpSeedItr.hasNext()) {
			int xpSeed = xpSeedItr.next();
			rand.setSeed(xpSeed);

			// check enchantment levels match
			for (int slot = 0; slot < 3; slot++) {
				int level = EnchantmentHelper.calcItemStackEnchantability(rand, slot, power, itemToEnchant);
				if (level < slot + 1) {
					level = 0;
				}
				level = ForgeEventFactory.onEnchantmentLevelSet(world, tablePos, slot, power, itemToEnchant, level);
				if (level != actualEnchantLevels[slot]) {
					xpSeedItr.remove();
					continue seedLoop;
				}
			}

			// generate enchantment clues and see if they match
			for (int slot = 0; slot < 3; slot++) {
				if (actualEnchantLevels[slot] > 0) {
					List<EnchantmentData> enchantments = getEnchantmentList(rand, xpSeed, itemToEnchant, slot,
							actualEnchantLevels[slot]);
					if (enchantments == null || enchantments.isEmpty()) {
						// check that there is indeed no enchantment clue
						if (actualEnchantmentClues[slot] != -1 || actualLevelClues[slot] != -1) {
							xpSeedItr.remove();
							continue seedLoop;
						}
					} else {
						// check the right enchantment clue was generated
						EnchantmentData clue = enchantments.get(rand.nextInt(enchantments.size()));
						if (Enchantment.getEnchantmentID(clue.enchantment) != actualEnchantmentClues[slot]
								|| clue.enchantmentLevel != actualLevelClues[slot]) {
							xpSeedItr.remove();
							continue seedLoop;
						}
					}
				}
			}
		}

		if (possibleXPSeeds.size() == 0) {
			TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.INVALID);
			LOGGER.warn(
					"Invalid enchantment seed information. Has the server got unknown mods, is there a desync, or is the client just bugged?");
		} else if (possibleXPSeeds.size() == 1) {
			TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.CRACKED_ENCH_SEED);
			if (!onFirstXPSeed) {
				addPlayerRNGInfo(possibleXPSeeds.iterator().next());
			}
			onFirstXPSeed = false;
		}
	}

	private static int getEnchantPower(World world, BlockPos tablePos) {
		float power = 0;

		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if ((dz != 0 || dx != 0) && world.isAirBlock(tablePos.add(dx, 0, dz))
						&& world.isAirBlock(tablePos.add(dx, 1, dz))) {
					power += ForgeHooks.getEnchantPower(world, tablePos.add(dx * 2, 0, dz * 2));
					power += ForgeHooks.getEnchantPower(world, tablePos.add(dx * 2, 1, dz * 2));
					if (dx != 0 && dz != 0) {
						power += ForgeHooks.getEnchantPower(world, tablePos.add(dx * 2, 0, dz));
						power += ForgeHooks.getEnchantPower(world, tablePos.add(dx * 2, 1, dz));
						power += ForgeHooks.getEnchantPower(world, tablePos.add(dx, 0, dz * 2));
						power += ForgeHooks.getEnchantPower(world, tablePos.add(dx, 1, dz * 2));
					}
				}
			}
		}

		return (int) power;
	}

	private static List<EnchantmentData> getEnchantmentList(Random rand, int xpSeed, ItemStack stack, int enchantSlot,
			int level) {
		rand.setSeed(xpSeed + enchantSlot);
		List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(rand, stack, level, false);

		if (stack.getItem() == Items.BOOK && list.size() > 1) {
			list.remove(rand.nextInt(list.size()));
		}

		return list;
	}

	private static void addPlayerRNGInfo(int enchantmentSeed) {
		EnumCrackState crackState = TempRules.ENCHANTING_CRACK_STATE.getValue();
		if (crackState == EnumCrackState.CRACKED) {
			return;
		}

		long newSeedHigh = (long) enchantmentSeed << 16;
		if (possiblePlayerRandSeeds.isEmpty() && crackState != EnumCrackState.INVALID) {
			for (int lowBits = 0; lowBits < 65536; lowBits++) {
				possiblePlayerRandSeeds.add(newSeedHigh | lowBits);
			}
		} else {
			// It's okay to allocate a new one, it will likely be small anyway
			Set<Long> newPlayerRandSeeds = new HashSet<>();
			for (long oldSeed : possiblePlayerRandSeeds) {
				long newSeed = (oldSeed * MULTIPLIER + ADDEND) & MASK;
				if ((newSeed & 0x0000_ffff_ffff_0000L) == newSeedHigh) {
					newPlayerRandSeeds.add(newSeed);
				}
			}
			possiblePlayerRandSeeds.clear();
			possiblePlayerRandSeeds.addAll(newPlayerRandSeeds);

			if (possiblePlayerRandSeeds.size() == 0) {
				TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.INVALID);
				LOGGER.warn(
						"Invalid player RNG information. Has the server got unknown mods, is there a desync, has an operator used /give, or is the client just bugged?");
			} else if (possiblePlayerRandSeeds.size() == 1) {
				TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.CRACKED);
				playerRand.setSeed(possiblePlayerRandSeeds.iterator().next() ^ MULTIPLIER);
				possiblePlayerRandSeeds.clear();
			}
		}
	}

	public static void onEnchantedItem() {
		if (!TempRules.ENCHANTING_PREDICTION.getValue()) {
			return;
		}
		EnumCrackState crackState = TempRules.ENCHANTING_CRACK_STATE.getValue();
		if (crackState == EnumCrackState.CRACKED) {
			possibleXPSeeds.clear();
			possibleXPSeeds.add(playerRand.nextInt());
		} else if (crackState == EnumCrackState.CRACKED_ENCH_SEED) {
			possibleXPSeeds.clear();
			TempRules.ENCHANTING_CRACK_STATE.setValue(EnumCrackState.CRACKING);
		} else {
			resetCracker();
			onFirstXPSeed = false;
		}
	}

	public static List<EnchantmentData> getEnchantmentsInTable(int slot) {
		EnumCrackState crackState = TempRules.ENCHANTING_CRACK_STATE.getValue();
		ContainerEnchantment enchContainer = (ContainerEnchantment) Minecraft.getMinecraft().player.openContainer;
		if (crackState != EnumCrackState.CRACKED_ENCH_SEED && crackState != EnumCrackState.CRACKED) {
			if (enchContainer.enchantClue[slot] == -1) {
				return null;
			}
			return Collections.singletonList(new EnchantmentData(
					Enchantment.getEnchantmentByID(enchContainer.enchantClue[slot]), enchContainer.worldClue[slot]));
		}
		Random rand = new Random();
		int xpSeed = possibleXPSeeds.iterator().next();
		ItemStack enchantingStack = enchContainer.tableInventory.getStackInSlot(0);
		int enchantLevels = enchContainer.enchantLevels[slot];
		return getEnchantmentList(rand, xpSeed, enchantingStack, slot, enchantLevels);
	}

	public static enum EnumCrackState implements IStringSerializable {
		UNCRACKED, CRACKING_ENCH_SEED, CRACKED_ENCH_SEED, CRACKING, CRACKED, INVALID;

		@Override
		public String getName() {
			return name();
		}
	}

}
