package net.earthcomputer.clientcommands.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.IClientCommand;

public abstract class ClientCommandBase extends CommandBase implements IClientCommand {

	@Override
	public boolean checkPermission(MinecraftServer p_checkPermission_1_, ICommandSender p_checkPermission_2_) {
		return true;
	}

	@Override
	public boolean allowUsageWithoutPrefix(ICommandSender arg0, String arg1) {
		return false;
	}

	protected static void ensureCreativeMode() throws CommandException {
		if (!Minecraft.getMinecraft().playerController.isInCreativeMode()) {
			throw new CommandException("This action requires creative mode");
		}
	}

}
