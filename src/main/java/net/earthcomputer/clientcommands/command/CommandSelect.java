package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.WorldEditSettings;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class CommandSelect extends ClientCommandBase {

	@Override
	public String getName() {
		return "cselect";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cselect.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		switch (args[0]) {
		case "start":
		case "end":
			if (args.length < 4) {
				throw new WrongUsageException(getUsage(sender));
			}
			BlockPos pos = parseBlockPos(sender, args, 1, false);
			if ("start".equals(args[0])) {
				WorldEditSettings.setSelectFrom(pos);
			} else {
				WorldEditSettings.setSelectTo(pos);
			}
			break;
		case "deselect":
			WorldEditSettings.deselect();
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

}
