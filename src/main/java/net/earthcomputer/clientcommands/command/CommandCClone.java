package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.gen.structure.StructureBoundingBox;

public class CommandCClone extends CommandAreaExtension {

	@Override
	public String getName() {
		return "cclone";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cclone.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 9) {
			throw new WrongUsageException(getUsage(sender));
		}

		sender = correctSender(sender);

		BlockPos pos1 = parseBlockPos(sender, args, 0, false);
		BlockPos pos2 = parseBlockPos(sender, args, 3, false);
		BlockPos destPos = parseBlockPos(sender, args, 6, false);

		// Validation
		if (args.length >= 10) {
			if ("filtered".equals(args[9])) {
				if (args.length < 12) {
					throw new WrongUsageException(getUsage(sender));
				}
				Block block = getBlockByText(sender, args[11]);
				if (args.length < 13) {
					convertArgToBlockStatePredicate(block, args[12]);
				}
			}
		}

		StructureBoundingBox sbbSrc = new StructureBoundingBox(pos1, pos2);
		StructureBoundingBox sbbDest = new StructureBoundingBox(destPos, destPos.add(sbbSrc.getLength()));
		if (sbbSrc.intersectsWith(sbbDest)) {
			if (args.length < 11 || (!"force".equals(args[10]) && !"move".equals(args[10]))) {
				throw new CommandException("commands.clone.noOverlap");
			} else {
				throw new CommandException("commands.cclone.overlapNotSupported");
			}
		}

		Vec3i relativePos = destPos.subtract(pos1);
		Object[] extraArgs = new Object[args.length - 9 + 1];
		extraArgs[0] = relativePos;
		System.arraycopy(args, 9, extraArgs, 1, args.length - 9);
		areaExecute(pos1, pos2, extraArgs);
	}

	@Override
	protected String makeCommand(BlockPos from, BlockPos to, Object[] extraArgs) {
		String[] strArgs = new String[extraArgs.length - 1];
		System.arraycopy(extraArgs, 1, strArgs, 0, strArgs.length);
		BlockPos dest = from.add((Vec3i) extraArgs[0]);
		return String.format("/clone %s %s %s %s %s %s %s %s %s %s", from.getX(), from.getY(), from.getZ(), to.getX(),
				to.getY(), to.getZ(), dest.getX(), dest.getY(), dest.getZ(), StringUtils.join(strArgs, " "));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 0) {
			return Collections.emptyList();
		} else if (args.length > 0 && args.length <= 3) {
			return getTabCompletionCoordinate(args, 0, targetPos);
		} else if (args.length > 3 && args.length <= 6) {
			return getTabCompletionCoordinate(args, 3, targetPos);
		} else if (args.length > 6 && args.length <= 9) {
			return getTabCompletionCoordinate(args, 6, targetPos);
		} else if (args.length == 10) {
			return getListOfStringsMatchingLastWord(args, "replace", "masked", "filtered");
		} else if (args.length == 11) {
			return getListOfStringsMatchingLastWord(args, "normal", "force", "move");
		} else if (args.length == 12) {
			if ("filtered".equals(args[9])) {
				return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
			} else {
				return Collections.emptyList();
			}
		}
		return Collections.emptyList();
	}

}
