package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.CreativeInventoryListener;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandCClear extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		ensureCreativeMode();

		EntityPlayer player = Minecraft.getMinecraft().player;
		Item item = args.length >= 1 ? getItemByText(sender, args[0]) : null;
		int damage = args.length >= 2 ? parseInt(args[1], -1) : -1;
		int maxCount = args.length >= 3 ? parseInt(args[2], -1) : -1;

		NBTTagCompound nbt = null;
		if (args.length >= 4) {
			try {
				nbt = JsonToNBT.getTagFromJson(buildString(args, 3));
			} catch (NBTException e) {
				throw new CommandException("commands.clear.tagError", e.getMessage());
			}
		}

		if (args.length >= 1 && item == null) {
			// item was specified but was null, invalid
			throw new CommandException("commands.clear.failure", player.getName());
		}

		// do the clearing
		int count = player.inventory.clearMatchingItems(item, damage, maxCount, nbt);
		CreativeInventoryListener listener = new CreativeInventoryListener();
		player.inventoryContainer.addListener(listener);
		player.inventoryContainer.detectAndSendChanges();
		player.inventoryContainer.removeListener(listener);

		// report the result
		if (count == 0) {
			throw new CommandException("commands.clear.failure", player.getName());
		}
		if (maxCount == 0) {
			sender.sendMessage(new TextComponentTranslation("commands.clear.testing", player.getName(), count));
		} else {
			sender.sendMessage(new TextComponentTranslation("commands.clear.success", player.getName(), count));
		}
	}

	@Override
	public String getName() {
		return "cclear";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cclear.usage";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
		} else {
			return Collections.emptyList();
		}
	}

}
