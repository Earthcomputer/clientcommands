package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.WorldEditSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSenderWrapper;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.IClientCommand;

public abstract class ClientCommandBase extends CommandBase implements IClientCommand {

	protected static ICommandSender correctSender(ICommandSender sender) {
		return new CommandSenderWrapper(sender, sender.getPositionVector(), sender.getPosition().add(-0.5, 0, -0.5), 0,
				sender.getCommandSenderEntity(), true) {
			@Override
			public boolean canUseCommand(int permLevel, String commandName) {
				return sender.canUseCommand(permLevel, commandName);
			}
		};
	}

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
			throw new CommandException("commands.client.notCreative");
		}
	}

	protected static ITextComponent getCoordsTextComponent(BlockPos pos) {
		ITextComponent text = new TextComponentTranslation("commands.client.blockpos", pos.getX(), pos.getY(),
				pos.getZ());
		text.getStyle().setUnderlined(true);
		text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ())));
		text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new TextComponentString(String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()))));
		return text;
	}

	protected static SelectionInfo parseSelectionInfo(ICommandSender sender, String[] args, int index)
			throws CommandException {
		try {
			if ("selection".equals(args[index])) {
				if (WorldEditSettings.hasSelection()) {
					return new SelectionInfo(WorldEditSettings.getSelectFrom(), WorldEditSettings.getSelectTo(), 1);
				} else {
					throw new CommandException("commands.generic.noSelection");
				}
			} else {
				BlockPos from = parseBlockPos(sender, args, index, false);
				BlockPos to = parseBlockPos(sender, args, index + 3, false);
				return new SelectionInfo(from, to, 6);
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new CommandException("commands.generic.invalidSelection");
		}
	}

	public static class SelectionInfo {
		public BlockPos from;
		public BlockPos to;
		public int argLen;

		public SelectionInfo(BlockPos from, BlockPos to, int argLen) {
			this.from = from;
			this.to = to;
			this.argLen = argLen;
		}
	}

}
