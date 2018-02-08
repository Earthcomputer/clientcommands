package net.earthcomputer.clientcommands.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandNote extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		sender.sendMessage(getChatComponentFromNthArg(sender, args, 0, false));
	}

	@Override
	public String getName() {
		return "cnote";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cnote.usage";
	}

}
