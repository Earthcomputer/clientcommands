package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.network.CreativeInventoryListener;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandCGive extends ClientCommandBase {

	@Override
	public String getName() {
		return "cgive";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cgive.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		ensureCreativeMode();

		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		Entity executingEntity = sender.getCommandSenderEntity();
		if (!(executingEntity instanceof EntityPlayer)) {
			throw new CommandException("commands.cgive.noPlayer");
		}
		EntityPlayer player = (EntityPlayer) executingEntity;

		Item item = getItemByText(sender, args[0]);
		int meta = args.length >= 3 ? parseInt(args[2]) : 0;

		// create the stack before the count, so we can reliably get the max. stack size
		// for modded items
		ItemStack stack = new ItemStack(item, 1, meta);

		if (args.length >= 4) {
			try {
				stack.setTagCompound(JsonToNBT.getTagFromJson(buildString(args, 3)));
			} catch (NBTException e) {
				throw new CommandException("commands.give.tagError", e.getMessage());
			}
		}

		int count = args.length >= 2 ? parseInt(args[1], 1, stack.getMaxStackSize()) : 1;
		stack.setCount(count);

		// do the giving
		boolean added = player.inventory.addItemStackToInventory(stack);
		if (added) {
			player.world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ITEM_PICKUP,
					SoundCategory.PLAYERS, 0.2f,
					((player.getRNG().nextFloat() - player.getRNG().nextFloat()) * 0.7f + 1f) * 2f);
			CreativeInventoryListener listener = new CreativeInventoryListener();
			player.inventoryContainer.addListener(listener);
			player.inventoryContainer.detectAndSendChanges();
			player.inventoryContainer.removeListener(listener);
		}

		// report the result
		if (!added) {
			throw new CommandException("commands.cgive.fullInventory");
		} else if (!stack.isEmpty()) {
			throw new CommandException("commands.cgive.notAll");
		} else {
			stack.setCount(1);
			sender.sendMessage(new TextComponentTranslation("commands.give.success", stack.getTextComponent(), count,
					player.getName()));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
		} else {
			return Collections.emptyList();
		}
	}

}
