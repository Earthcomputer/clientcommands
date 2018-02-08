package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandAbort extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (TaskManager.abortTasks()) {
			sender.sendMessage(new TextComponentTranslation("commands.cabort.success"));
		} else {
			throw new CommandException("commands.cabort.noTask");
		}
	}

	@Override
	public String getName() {
		return "cabort";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cabort.usage";
	}

}
