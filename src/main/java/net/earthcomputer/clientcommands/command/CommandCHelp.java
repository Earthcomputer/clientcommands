package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandHelp;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.IClientCommand;

public class CommandCHelp extends CommandHelp implements IClientCommand {

	@Override
	public List<String> getAliases() {
		return Collections.emptyList();
	}

	@Override
	protected Map<String, ICommand> getCommandMap(MinecraftServer server) {
		return ClientCommandHandler.instance.getCommands();
	}

	@Override
	public String getName() {
		return "chelp";
	}

	@Override
	protected List<ICommand> getSortedPossibleCommands(ICommandSender sender, MinecraftServer server) {
		List<ICommand> commands = ClientCommandHandler.instance.getPossibleCommands(sender);
		Collections.sort(commands);
		return commands;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.chelp.usage";
	}

	@Override
	public boolean allowUsageWithoutPrefix(ICommandSender sender, String arg1) {
		return false;
	}

}
