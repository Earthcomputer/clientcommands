package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.earthcomputer.clientcommands.EventManager.Listener;
import net.earthcomputer.clientcommands.network.NetUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

public class TempRulesImpl {

	private TempRulesImpl() {
	}

	public static void registerEvents() {
		initBlockReachDistance();
		initToolBreakProtection();
		initGhostBlockFix();
		initAxeWand();
	}

	private static void initBlockReachDistance() {
		TempRules.BLOCK_REACH_DISTANCE.addValueChangeListener(e -> {
			Minecraft.getMinecraft().player.getAttributeMap().getAttributeInstance(EntityPlayer.REACH_DISTANCE)
					.setBaseValue(e.getNewValue());
		});
	}

	private static AtomicBoolean isDesynced = new AtomicBoolean(false);

	private static void initToolBreakProtection() {
		EventManager.addPlayerTickListener(e -> {
			if (isDesynced.getAndSet(false)) {
				NetUtils.resyncInventory(slot -> slot.inventory == Minecraft.getMinecraft().player.inventory);
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
					isDesynced.set(true);
				}
			}
		});
	}

	private static List<BlockPos> blocksToUpdate = new ArrayList<>();

	private static void initGhostBlockFix() {
		EventManager.addAttackBlockListener(e -> {
			if (TempRules.GHOST_BLOCK_FIX.getValue()) {
				// Test conditions for instant-mining
				PlayerControllerMP controller = Minecraft.getMinecraft().playerController;
				IBlockState state = e.getWorld().getBlockState(e.getPos());
				boolean canInstaMine = state.getMaterial() != Material.AIR
						&& state.getPlayerRelativeBlockHardness(e.getEntityPlayer(), e.getWorld(), e.getPos()) >= 1;
				if (controller.isNotCreative() && canInstaMine) {
					blocksToUpdate.add(e.getPos());
				}
			}
		});
		EventManager.addPlayerTickListener(e -> {
			if (!blocksToUpdate.isEmpty()) {
				for (BlockPos pos : blocksToUpdate) {
					// Cause the server to re-send the block
					NetUtils.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos,
							EnumFacing.DOWN));
				}
				blocksToUpdate.clear();
			}
		});
	}

	private static void initAxeWand() {
		EventManager.addUseItemListener(e -> {
			if (WorldEditSettings.isWand(e.getItemStack())) {
				RayTraceResult rayTrace = Minecraft.getMinecraft().objectMouseOver;
				BlockPos pos;
				if (rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
					pos = rayTrace.getBlockPos();
				} else {
					pos = new BlockPos(Minecraft.getMinecraft().player);
				}
				WorldEditSettings.setSelectTo(pos);
				e.setCanceled(true);
			}
		});
		EventManager.addAttackAirListener(e -> {
			if (WorldEditSettings.isWand(e.getItemStack())) {
				WorldEditSettings.setSelectFrom(e.getPos());
			}
		});
		EventManager.addAttackBlockListener(e -> {
			if (WorldEditSettings.isWand(e.getItemStack())) {
				WorldEditSettings.setSelectFrom(e.getPos());
				EventManager.addEndTickListener(new Listener<ClientTickEvent>() {
					@Override
					public void accept(ClientTickEvent e) {
						Minecraft.getMinecraft().playerController.resetBlockRemoving();
					}

					@Override
					public boolean wasFinalAction() {
						return true;
					}
				});
				e.setCanceled(true);
			}
		});
	}

}
