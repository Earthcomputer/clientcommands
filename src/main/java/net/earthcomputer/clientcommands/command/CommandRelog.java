package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.cvw.ServerConnector;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandRelog extends ClientCommandBase {

	// flag to stop disconnecting event listeners from being triggered by the
	// /crelog command (mostly for convenience)
	public static boolean isRelogging = false;

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		isRelogging = true;
		ServerConnector connector = ServerConnector.forCurrentServer();
		connector.disconnect();
		connector.connect();
	}

	@Override
	public String getName() {
		return "crelog";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.crelog.usage";
	}

}
