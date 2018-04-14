package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.earthcomputer.clientcommands.network.NetUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.BlockStructure;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

public class ToolDamageManager {

	private ToolDamageManager() {
	}

	private static List<Class<? extends Packet<?>>> packetsToBlock = new ArrayList<>();
	private static int timesToBlockUseItem = 0;
	private static int timesToBlockStopUseItem = 0;
	private static int timesToBlockAttackEntity = 0;
	private static int timesToBlockUseEntity = 0;

	public static void registerEvents() {
		EventManager.addOutboundPacketPreListener(e -> {
			Iterator<Class<? extends Packet<?>>> packetItr = packetsToBlock.iterator();
			while (packetItr.hasNext()) {
				if (packetItr.next().isInstance(e.getPacket())) {
					e.setCanceled(true);
					packetItr.remove();
					return;
				}
			}
		});
		EventManager.addPlayerTickListener(e -> {
			EntityPlayer player = e.player;
			if (player.isElytraFlying()) {
				ItemStack elytra = player.inventory.armorItemInSlot(2);
				if (!elytra.isEmpty() && elytra.getItem() == Items.ELYTRA) {
					if (postToolDamaged(player, elytra, 1)) {
						NetUtils.sendPacket(
								new CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING));
					}
				}
			}
		});
		EventManager.addUseBlockListener(e -> {
			ItemStack stack = e.getItemStack();
			if (stack.isEmpty()) {
				return;
			}
			Item item = stack.getItem();

			IBlockState state = e.getWorld().getBlockState(e.getPos());
			if (state.getBlock() == Blocks.TNT) {
				if (item == Items.FLINT_AND_STEEL) {
					if (postToolDamaged(e.getEntityPlayer(), stack, 1)) {
						e.setCanceled(true);
						packetsToBlock.add(CPacketPlayerTryUseItemOnBlock.class);
					}
					return;
				}
			}
			if (e.getEntityPlayer().canPlayerEdit(e.getPos().offset(e.getFace()), e.getFace(), stack)) {
				if (item instanceof ItemHoe || item == Items.FLINT_AND_STEEL) {
					// tilling the ground or setting it on fire
					if (postToolDamaged(e.getEntityPlayer(), stack, 1)) {
						e.setCanceled(true);
						packetsToBlock.add(CPacketPlayerTryUseItemOnBlock.class);
					}
					return;
				} else if (item instanceof ItemSpade) {
					// creating a grass path
					if (e.getFace() != EnumFacing.DOWN
							&& e.getWorld().getBlockState(e.getPos().up()).getMaterial() == Material.AIR
							&& e.getWorld().getBlockState(e.getPos()).getBlock() == Blocks.GRASS) {
						if (postToolDamaged(e.getEntityPlayer(), stack, 1)) {
							e.setCanceled(true);
							packetsToBlock.add(CPacketPlayerTryUseItemOnBlock.class);
						}
						return;
					}
				}
			}
		});
		EventManager.addAttackBlockListener(e -> {
			if (!Minecraft.getMinecraft().playerController.isInCreativeMode()) {
				IBlockState hitState = e.getWorld().getBlockState(e.getPos());
				int damage = getDestroyBlockDamage(e.getWorld(), e.getPos(), e.getEntityPlayer());
				if (damage != 0) {
					if (postToolDamagedPre(e.getEntityPlayer(), e.getItemStack(), damage)) {
						e.setCanceled(true);
						packetsToBlock.add(CPacketPlayerDigging.class);
					} else if (hitState.getMaterial() != Material.AIR && hitState
							.getPlayerRelativeBlockHardness(e.getEntityPlayer(), e.getWorld(), e.getPos()) >= 1) {
						// handle end of instant-mine
						postToolDamagedPost(e.getEntityPlayer(), e.getItemStack(), damage);
					}
				}
			}
		});
		// need to listen for the packet since it's dispatched before the normal event
		EventManager.addOutboundPacketPreListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof CPacketPlayerTryUseItem) {
				CPacketPlayerTryUseItem useItem = (CPacketPlayerTryUseItem) packet;

				EntityPlayer player = Minecraft.getMinecraft().player;
				ItemStack stack = player.getHeldItem(useItem.getHand());
				if (stack.isEmpty()) {
					return;
				}
				Item item = stack.getItem();

				if (player.getCooldownTracker().hasCooldown(item)) {
					return;
				}

				if (item == Items.CARROT_ON_A_STICK) {
					if (postToolDamaged(player, stack, 7)) {
						e.setCanceled(true);
						timesToBlockUseItem++;
					}
					return;
				} else if (item instanceof ItemFishingRod) {
					if (postToolDamaged(player, stack, 5)) { // assume 5 (max in vanilla)
						e.setCanceled(true);
						timesToBlockUseItem++;
					}
					return;
				}
			} else if (packet instanceof CPacketUseEntity) {
				CPacketUseEntity useEntity = (CPacketUseEntity) packet;

				if (Minecraft.getMinecraft().playerController.isSpectator()) {
					return;
				}
				if (useEntity.getAction() == CPacketUseEntity.Action.ATTACK) {
					EntityPlayer player = Minecraft.getMinecraft().player;
					ItemStack stack = player.getHeldItemMainhand();
					if (stack.isEmpty()) {
						return;
					}
					Item item = stack.getItem();

					int damage = 0;
					if (item instanceof ItemSword || item instanceof ItemHoe) {
						damage = 1;
					} else if (item instanceof ItemTool) {
						damage = 2;
					}
					if (damage != 0) {
						if (postToolDamaged(player, stack, damage)) {
							e.setCanceled(true);
							timesToBlockAttackEntity++;
						}
					}
				} else if (useEntity.getAction() == CPacketUseEntity.Action.INTERACT) {
					boolean isDamagingItem = false;
					EntityPlayer player = Minecraft.getMinecraft().player;
					ItemStack stack = player.getHeldItem(useEntity.getHand());
					if (stack.isEmpty()) {
						return;
					}
					Item item = stack.getItem();
					Entity target = useEntity.getEntityFromWorld(Minecraft.getMinecraft().world);

					if (item instanceof ItemShears) {
						if (target instanceof IShearable) {
							if (((IShearable) target).isShearable(stack, target.world, new BlockPos(target))) {
								// shearing sheep, snow golems
								isDamagingItem = true;
							}
						}
					} else if (item == Items.FLINT_AND_STEEL) {
						if (target instanceof EntityCreeper) {
							isDamagingItem = true;
						}
					}

					if (isDamagingItem) {
						if (postToolDamaged(player, stack, 1)) {
							e.setCanceled(true);
							timesToBlockUseEntity++;
						}
						return;
					}
				}
			} else if (packet instanceof CPacketPlayerDigging) {
				CPacketPlayerDigging playerDigging = (CPacketPlayerDigging) packet;
				if (playerDigging.getAction() == CPacketPlayerDigging.Action.RELEASE_USE_ITEM) {
					EntityPlayer player = Minecraft.getMinecraft().player;
					if (player.capabilities.isCreativeMode) {
						return;
					}
					ItemStack stack = player.getActiveItemStack();
					if (stack.isEmpty()) {
						return;
					}
					Item item = stack.getItem();

					if (item instanceof ItemBow) {
						boolean hasAmmo = false;
						for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
							ItemStack arrow = player.inventory.getStackInSlot(i);
							if (!arrow.isEmpty() && arrow.getItem() instanceof ItemArrow) {
								hasAmmo = true;
								break;
							}
						}
						int charge = stack.getMaxItemUseDuration() - player.getItemInUseCount();

						if (hasAmmo && ItemBow.getArrowVelocity(charge) >= 0.1) {
							if (postToolDamaged(player, stack, 1)) {
								e.setCanceled(true);
								timesToBlockStopUseItem++;
							}
						}
					}
				} else if (playerDigging.getAction() == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
					// the player has completed mining a block
					Minecraft mc = Minecraft.getMinecraft();
					int damage = getDestroyBlockDamage(mc.world, playerDigging.getPosition(), mc.player);
					if (damage != 0) {
						postToolDamagedPost(mc.player, mc.player.getHeldItemMainhand(), damage);
					}
				}
			}
		});
		EventManager.addUseItemListener(e -> {
			if (timesToBlockUseItem > 0) {
				timesToBlockUseItem--;
				e.setCanceled(true);
			}
		});
		EventManager.addStopUseItemListener(e -> {
			if (timesToBlockStopUseItem > 0) {
				timesToBlockStopUseItem--;
				e.setCanceled(true);
			}
		});
		EventManager.addAttackEntityListener(e -> {
			if (timesToBlockAttackEntity > 0) {
				timesToBlockAttackEntity--;
				e.setCanceled(true);
			}
		});
		EventManager.addUseEntityListener(e -> {
			if (timesToBlockUseEntity > 0) {
				timesToBlockUseEntity--;
				e.setCanceled(true);
			}
		});
	}

	private static int getDestroyBlockDamage(World world, BlockPos pos, EntityPlayer player) {
		IBlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		ItemStack stack = player.getHeldItemMainhand();
		Minecraft mc = Minecraft.getMinecraft();

		// perform necessary extra checks
		if (mc.playerController.getCurrentGameType().hasLimitedInteractions()) {
			if (mc.playerController.getCurrentGameType() == GameType.SPECTATOR) {
				return 0;
			}
			if (!player.isAllowEdit()) {
				if (stack.isEmpty()) {
					return 0;
				}
				if (!stack.canDestroy(block)) {
					return 0;
				}
			}
		}
		if (block instanceof BlockCommandBlock || block instanceof BlockStructure) {
			return 0;
		}
		if (state.getMaterial() == Material.AIR) {
			return 0;
		}

		// perform the item use check on the item if necessary
		if (!stack.isEmpty()) {
			Item item = stack.getItem();
			if (item instanceof ItemShears) {
				return 1;
			} else if (item instanceof ItemSword || item instanceof ItemTool) {
				if (world.getBlockState(pos).getBlockHardness(world, pos) != 0) {
					return item instanceof ItemSword ? 2 : 1;
				}
			}
		}

		return 0;
	}

	private static boolean postToolDamaged(EntityPlayer player, ItemStack stack, int damageAmount) {
		if (postToolDamagedPre(player, stack, damageAmount)) {
			return true;
		} else {
			postToolDamagedPost(player, stack, damageAmount);
			return false;
		}
	}

	private static boolean postToolDamagedPre(EntityPlayer player, ItemStack stack, int damageAmount) {
		return MinecraftForge.EVENT_BUS.post(new ToolDamagedEvent.Pre(player, stack, damageAmount));
	}

	private static void postToolDamagedPost(EntityPlayer player, ItemStack stack, int damageAmount) {
		MinecraftForge.EVENT_BUS.post(new ToolDamagedEvent.Post(player, stack, damageAmount));
	}

	public static class ToolDamagedEvent extends PlayerEvent {

		private ItemStack stack;
		private int damageAmount;

		public ToolDamagedEvent(EntityPlayer player, ItemStack stack, int damageAmount) {
			super(player);
			this.stack = stack;
			this.damageAmount = damageAmount;
		}

		public ItemStack getItemStack() {
			return stack;
		}

		public int getDamageAmount() {
			return damageAmount;
		}

		@Cancelable
		public static class Pre extends ToolDamagedEvent {
			public Pre(EntityPlayer player, ItemStack stack, int damageAmount) {
				super(player, stack, damageAmount);
			}
		}

		public static class Post extends ToolDamagedEvent {
			public Post(EntityPlayer player, ItemStack stack, int damageAmount) {
				super(player, stack, damageAmount);
			}
		}

	}

}
