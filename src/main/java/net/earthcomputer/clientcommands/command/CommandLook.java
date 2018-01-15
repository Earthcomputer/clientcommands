package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CommandLook extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		String mode = args[0];
		switch (mode) {
		case "block":
			lookBlock(sender, args);
			break;
		case "angles":
			lookAngles(sender, args);
			break;
		case "cardinal":
			lookCardinal(sender, args);
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private void lookBlock(ICommandSender sender, String... args) throws CommandException {
		if (args.length < 4) {
			throw new WrongUsageException("/clook block <x> <y> <z>");
		}

		BlockPos pos = parseBlockPos(sender, args, 1, true);
		Vec3d entPos = sender.getPositionVector();
		double dx = pos.getX() + 0.5 - entPos.x;
		double dy = pos.getY() + 0.5 - (entPos.y + sender.getCommandSenderEntity().getEyeHeight());
		double dz = pos.getZ() + 0.5 - entPos.z;
		double dh = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
		doLook(sender, yaw, pitch);
	}

	private void lookAngles(ICommandSender sender, String... args) throws CommandException {
		if (args.length < 3) {
			throw new WrongUsageException("/clook angles <yaw> <pitch>");
		}

		float yaw = (float) parseCoordinate(sender.getCommandSenderEntity().rotationYaw, args[1], false).getResult();
		float pitch = (float) parseCoordinate(sender.getCommandSenderEntity().rotationPitch, args[2], false)
				.getResult();
		doLook(sender, yaw, pitch);
	}

	private void lookCardinal(ICommandSender sender, String... args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/clook cardinal <west|east|down|up|north|south>");
		}

		EnumFacing direction = EnumFacing.byName(args[1]);
		if (direction == null) {
			throw new WrongUsageException("/clook cardinal <west|east|down|up|north|south>");
		}
		switch (direction) {
		case DOWN:
			doLook(sender, sender.getCommandSenderEntity().rotationYaw, 90);
			break;
		case EAST:
			doLook(sender, -90, 0);
			break;
		case NORTH:
			doLook(sender, -180, 0);
			break;
		case SOUTH:
			doLook(sender, 0, 0);
			break;
		case UP:
			doLook(sender, sender.getCommandSenderEntity().rotationYaw, -90);
			break;
		case WEST:
			doLook(sender, 90, 0);
			break;
		}
	}

	private void doLook(ICommandSender sender, float yaw, float pitch) {
		sender.getCommandSenderEntity().setLocationAndAngles(sender.getCommandSenderEntity().posX,
				sender.getCommandSenderEntity().posY, sender.getCommandSenderEntity().posZ, MathHelper.wrapDegrees(yaw),
				pitch);
	}

	@Override
	public String getName() {
		return "clook";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/clook <block|angles|cardinal> ...";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 0) {
			return Collections.emptyList();
		}
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "block", "angles", "cardinal");
		}
		if ("cardinal".equals(args[1])) {
			return getListOfStringsMatchingLastWord(args, "west", "east", "down", "up", "north", "south");
		}
		return Collections.emptyList();
	}

}
