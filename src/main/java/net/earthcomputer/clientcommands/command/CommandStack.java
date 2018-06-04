package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class CommandStack extends ClientCommandBase {

	private static final CommandCClone CCLONE = new CommandCClone();

	@Override
	public String getName() {
		return "cstack";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cstack.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		SelectionInfo selectionInfo = parseSelectionInfo(sender, args, 0);
		int index = selectionInfo.argLen;

		BlockPos pos1 = new BlockPos(Math.min(selectionInfo.from.getX(), selectionInfo.to.getX()),
				Math.min(selectionInfo.from.getY(), selectionInfo.to.getY()),
				Math.min(selectionInfo.from.getZ(), selectionInfo.to.getZ()));
		BlockPos pos2 = new BlockPos(Math.max(selectionInfo.from.getX(), selectionInfo.to.getX()),
				Math.max(selectionInfo.from.getY(), selectionInfo.to.getY()),
				Math.max(selectionInfo.from.getZ(), selectionInfo.to.getZ()));
		int xSize = pos2.getX() - pos1.getX() + 1;
		int ySize = pos2.getY() - pos1.getY() + 1;
		int zSize = pos2.getZ() - pos1.getZ() + 1;

		int count = parseInt(args[index++], 1, 1000);

		int dx, dy, dz;
		if (index >= args.length) {
			EnumFacing facing = getPlayerFacing();
			dx = facing.getFrontOffsetX() * xSize;
			dy = facing.getFrontOffsetY() * ySize;
			dz = facing.getFrontOffsetZ() * zSize;
		} else {
			switch (args[index]) {
			case "west":
			case "east":
			case "down":
			case "up":
			case "north":
			case "south":
				EnumFacing facing = EnumFacing.byName(args[index++]);
				dx = facing.getFrontOffsetX() * xSize;
				dy = facing.getFrontOffsetY() * ySize;
				dz = facing.getFrontOffsetZ() * zSize;
				break;
			default:
				if (index + 3 >= args.length)
					throw new WrongUsageException(getUsage(sender));
				dx = parseInt(args[index++], 0, 1000);
				dy = parseInt(args[index++], 0, sender.getEntityWorld().getHeight());
				dz = parseInt(args[index++], 0, 1000);
				// TODO: validation
				break;
			}
		}

		int x = pos1.getX();
		int y = pos1.getY();
		int z = pos1.getZ();
		for (int i = 0; i < count; i++) {
			x += dx;
			y += dy;
			z += dz;
			String[] newArgs = new String[9 + Math.max(args.length - index, 0)];
			newArgs[0] = String.valueOf(pos1.getX());
			newArgs[1] = String.valueOf(pos1.getY());
			newArgs[2] = String.valueOf(pos1.getZ());
			newArgs[3] = String.valueOf(pos2.getX());
			newArgs[4] = String.valueOf(pos2.getY());
			newArgs[5] = String.valueOf(pos2.getZ());
			newArgs[6] = String.valueOf(x);
			newArgs[7] = String.valueOf(y);
			newArgs[8] = String.valueOf(z);
			System.arraycopy(args, index, newArgs, 9, Math.max(args.length - index, 0));
			CCLONE.execute(server, sender, newArgs);
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 0) {
			return Collections.emptyList();
		} else if (args.length == 1) {
			List<String> tabs = new ArrayList<>();
			tabs.add("selection");
			tabs.addAll(getTabCompletionCoordinate(args, 0, targetPos));
			return tabs;
		}
		boolean selection = "selection".equals(args[0]);
		if (selection) {
			if (args.length > 0 && args.length <= 3) {
				return getTabCompletionCoordinate(args, 0, targetPos);
			} else if (args.length > 3 && args.length <= 6) {
				return getTabCompletionCoordinate(args, 3, targetPos);
			}
		}
		int index = selection ? 1 : 6;
		index++; // count
		if (args.length == index) {
			return getListOfStringsMatchingLastWord(args, "west", "east", "down", "up", "north", "south");
		}
		switch (args[index]) {
		case "west":
		case "east":
		case "down":
		case "up":
		case "north":
		case "south":
			index++;
		default:
			index += 3;
		}
		if (args.length == index) {
			return getListOfStringsMatchingLastWord(args, "replace", "masked", "filtered");
		}
		return Collections.emptyList();
	}

	private static EnumFacing getPlayerFacing() {
		EntityPlayer player = Minecraft.getMinecraft().player;
		if (player.rotationPitch > 45)
			return EnumFacing.DOWN;
		else if (player.rotationPitch < -45)
			return EnumFacing.UP;
		else
			return EnumFacing.fromAngle(player.rotationYaw);
	}

}
