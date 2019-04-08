package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.earthcomputer.clientcommands.render.RenderSettings;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandRender extends ClientCommandBase {

	@Override
	public String getName() {
		return "crender";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.crender.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(sender));
		}

		boolean enable;
		switch (args[0]) {
		case "enable":
			enable = true;
			break;
		case "disable":
			enable = false;
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}

		switch (args[1]) {
		case "entities":
			toggleEntities(sender, args, enable);
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private void toggleEntities(ICommandSender sender, String[] args, boolean enable) throws CommandException {
		Set<ResourceLocation> types;
		NBTTagCompound nbt = new NBTTagCompound();
		if (args.length < 3) {
			types = EntityList.getEntityNameList();
		} else {
			types = new HashSet<>();
			int i;
			for (i = 2; i < args.length; i++) {
				if (args[i].startsWith("{"))
					break;
				ResourceLocation type = new ResourceLocation(args[i]);
				if (!EntityList.isRegistered(type)) {
					throw new CommandException("commands.crender.entities.unknown", type);
				}
				types.add(type);
			}
			if (i != args.length) {
				try {
					nbt = JsonToNBT.getTagFromJson(buildString(args, i));
				} catch (NBTException e) {
					throw new CommandException("commands.scoreboard.players.set.tagError", e.getMessage());
				}
			}
		}
		for (ResourceLocation type : types) {
			Class<? extends Entity> clazz = EntityList.getClass(type);
			RenderSettings.addRenderingFilter(clazz, nbt, enable);
		}
		if (enable)
			sender.sendMessage(new TextComponentTranslation("commands.crender.entities.enable.success", types.size()));
		else
			sender.sendMessage(new TextComponentTranslation("commands.crender.entities.disable.success", types.size()));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 0) {
			return Collections.emptyList();
		} else if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "enable", "disable");
		} else if (args.length == 2) {
			return getListOfStringsMatchingLastWord(args, "entities");
		} else if (args.length == 3) {
			if ("entities".equals(args[1])) {
				return getListOfStringsMatchingLastWord(args, EntityList.getEntityNameList());
			} else {
				return Collections.emptyList();
			}
		} else {
			return Collections.emptyList();
		}
	}

}
