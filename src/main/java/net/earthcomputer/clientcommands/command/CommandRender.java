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
		if (args.length < 3) {
			types = EntityList.getEntityNameList();
		} else {
			types = new HashSet<>();
			for (int i = 2; i < args.length; i++) {
				ResourceLocation type = new ResourceLocation(args[i]);
				if (!EntityList.isRegistered(type)) {
					throw new CommandException("commands.crender.entities.unknown", type);
				}
				types.add(type);
			}
		}
		int count = 0;
		for (ResourceLocation type : types) {
			Class<? extends Entity> clazz = EntityList.getClass(type);
			if (RenderSettings.isEntityRenderingDisabled(clazz) == enable) {
				count++;
				if (enable)
					RenderSettings.enableEntityRendering(clazz);
				else
					RenderSettings.disableEntityRendering(clazz);
			}
		}
		if (enable)
			sender.sendMessage(new TextComponentTranslation("commands.crender.entities.enable.success", count));
		else
			sender.sendMessage(new TextComponentTranslation("commands.crender.entities.disable.success", count));
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
