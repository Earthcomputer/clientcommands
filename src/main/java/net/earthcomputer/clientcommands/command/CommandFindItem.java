package net.earthcomputer.clientcommands.command;

import java.util.List;

import net.earthcomputer.clientcommands.task.GuiBlocker;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.DelegatingContainer;
import net.earthcomputer.clientcommands.util.Ptr;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class CommandFindItem extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		TaskManager.ensureNoTasks();

		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		Item item = getItemByText(sender, args[0]);
		int damage = args.length < 2 || "*".equals(args[1]) ? -1 : parseInt(args[1], -1, Short.MAX_VALUE);
		NBTTagCompound nbt = null;
		if (args.length >= 3) {
			try {
				nbt = JsonToNBT.getTagFromJson(getChatComponentFromNthArg(sender, args, 2).getUnformattedText());
			} catch (NBTException e) {
				throw new CommandException("commands.give.tagError", e.getMessage());
			}
		}

		Minecraft mc = Minecraft.getMinecraft();
		World world = mc.world;

		// Check the player's inventory first, since the player having a matching item
		// in their inventory would screw with the search process
		for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
			ItemStack stack = mc.player.inventory.getStackInSlot(i);
			if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)
					&& (nbt == null || NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true))) {
				sender.sendMessage(new TextComponentTranslation("commands.cfinditem.alreadyPresent"));
				return;
			}
		}

		if (mc.player.isSneaking()) {
			sender.sendMessage(
					new TextComponentString(TextFormatting.RED + I18n.format("commands.cfinditem.sneaking")));
			return;
		}

		// +0.5 because we're going to the center of blocks. Not a perfect heuristic,
		// but better than nothing.
		float radius = mc.playerController.getBlockReachDistance() + 0.5f;
		double playerx = sender.getPositionVector().x;
		double playery = sender.getPositionVector().y + sender.getCommandSenderEntity().getEyeHeight();
		double playerz = sender.getPositionVector().z;

		Ptr<Integer> itemsFound = new Ptr<>(0);

		// add queued search task for each inventory
		for (int x = (int) (playerx - radius); x <= playerx + radius; x++) {
			for (int z = (int) (playerz - radius); z <= playerz + radius; z++) {
				for (int y = (int) (playery - radius); y <= playery + radius; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (shouldSearch(world, pos)) {
						TaskManager.addLongTask(new WaitForGuiTask(mc, item, damage, nbt, sender, pos, itemsFound));
					}
				}
			}
		}

		final NBTTagCompound nbt_f = nbt;
		// add a queued "finished" notification task
		TaskManager.addLongTask(new LongTask() {
			@Override
			public void start() {
				int noItemsFound = itemsFound.get();
				if (noItemsFound == 0) {
					sender.sendMessage(
							new TextComponentString(TextFormatting.RED + I18n.format("commands.cfinditem.noMatch")));
				} else {
					sender.sendMessage(new TextComponentTranslation("commands.cfinditem.success", noItemsFound,
							new ItemStack(item, 1, damage, nbt_f).getDisplayName()));
				}
				setFinished();
			}

			@Override
			protected void taskTick() {
			}
		});
	}

	@Override
	public String getName() {
		return "cfinditem";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cfinditem.usage";
	}

	private static boolean shouldSearch(World world, BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();
		TileEntity te = world.getTileEntity(pos);

		if (block == Blocks.ENDER_CHEST) {
			return true;
		}

		if (block instanceof BlockChest) {
			if (!(te instanceof TileEntityChest)) {
				return false;
			}
			TileEntityChest chest = (TileEntityChest) te;
			if (!chest.adjacentChestChecked) {
				chest.checkForAdjacentChests();
			}
			return chest.adjacentChestXNeg == null && chest.adjacentChestZNeg == null;
		}

		if (te instanceof IInventory) {
			return true;
		}

		return false;
	}

	private static class WaitForGuiTask extends LongTask {
		private Minecraft mc;
		private Item item;
		private int damage;
		private NBTTagCompound nbt;
		private ICommandSender sender;
		private BlockPos pos;
		private Ptr<Integer> itemsFound;

		public WaitForGuiTask(Minecraft mc, Item item, int damage, NBTTagCompound nbt, ICommandSender sender,
				BlockPos pos, Ptr<Integer> itemsFound) {
			this.mc = mc;
			this.item = item;
			this.damage = damage;
			this.nbt = nbt;
			this.sender = sender;
			this.pos = pos;
			this.itemsFound = itemsFound;
		}

		@Override
		public void start() {
			// create a GUI blocker which listens for when the items are received on the
			// client
			GuiBlocker blocker = new GuiBlocker() {
				@Override
				public boolean processGui(GuiScreen gui) {
					if (gui instanceof GuiContainer) {
						GuiContainer containerGui = (GuiContainer) gui;
						mc.player.openContainer = new DelegatingContainer(containerGui.inventorySlots) {
							@Override
							public void setAll(List<ItemStack> stacks) {
								for (ItemStack stack : stacks) {
									if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)
											&& (nbt == null
													|| NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true))) {
										sender.sendMessage(new TextComponentTranslation("commands.cfinditem.match.left")
												.appendSibling(getCoordsTextComponent(pos))
												.appendSibling(new TextComponentTranslation(
														"commands.cfinditem.match.right")));
										itemsFound.set(itemsFound.get() + stack.getCount());
									}
								}
								mc.player.closeScreen();
								WaitForGuiTask.this.setFinished();
							}
						};
						setFinished();
						return false;
					} else {
						return true;
					}
				}
			};
			TaskManager.addGuiBlocker(blocker);

			// try to right click the block to cause the server to send the container to the
			// client
			boolean success = false;
			for (EnumHand hand : EnumHand.values()) {
				EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world, pos,
						EnumFacing.DOWN, new Vec3d(pos), hand);
				if (result == EnumActionResult.FAIL) {
					sender.sendMessage(new TextComponentString(
							TextFormatting.RED + I18n.format("commands.cfinditem.unableToOpen")));
					break;
				}
				if (result == EnumActionResult.SUCCESS) {
					success = true;
					break;
				}
			}
			if (!success) {
				blocker.setFinished();
			}
		}

		@Override
		protected void taskTick() {
		}
	}

}
