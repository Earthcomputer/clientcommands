package net.earthcomputer.clientcommands.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
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

	protected static ITextComponent getCoordsTextComponent(BlockPos pos) {
		ITextComponent text = new TextComponentString(
				String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()));
		text.getStyle().setUnderlined(true);
		text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ())));
		text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new TextComponentString(String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()))));
		return text;
	}

}
