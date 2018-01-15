package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandAbort extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (TaskManager.abortTasks()) {
			sender.sendMessage(new TextComponentString("Successfully aborted the current task"));
		} else {
			throw new CommandException("No task to abort");
		}
	}

	@Override
	public String getName() {
		return "cabort";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/cabort";
	}

}
