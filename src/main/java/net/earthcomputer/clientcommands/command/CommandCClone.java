package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
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
		sender = correctSender(sender);

		SelectionInfo selectionInfo = parseSelectionInfo(sender, args, 0);
		BlockPos pos1 = selectionInfo.from;
		BlockPos pos2 = selectionInfo.to;

		int index = selectionInfo.argLen;

		if (args.length < index + 3) {
			throw new WrongUsageException(getUsage(sender));
		}
		BlockPos destPos = parseBlockPos(sender, args, index, false);

		index += 3;

		// Validation
		if (args.length > index) {
			if ("filtered".equals(args[index])) {
				if (args.length < index + 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				Block block = getBlockByText(sender, args[index + 1]);
				if (args.length > index + 2) {
					convertArgToBlockStatePredicate(block, args[index + 2]);
				}
			}
		}

		StructureBoundingBox sbbSrc = new StructureBoundingBox(pos1, pos2);
		StructureBoundingBox sbbDest = new StructureBoundingBox(destPos, destPos.add(sbbSrc.getLength()));
		if (sbbSrc.intersectsWith(sbbDest)) {
			if (args.length < index + 2 || (!"force".equals(args[index + 1]) && !"move".equals(args[index + 1]))) {
				throw new CommandException("commands.clone.noOverlap");
			} else {
				throw new CommandException("commands.cclone.overlapNotSupported");
			}
		}

		Vec3i relativePos = destPos.subtract(pos1);
		Object[] extraArgs = new Object[args.length - index + 1];
		extraArgs[0] = relativePos;
		System.arraycopy(args, index, extraArgs, 1, args.length - index);
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
		if (args.length > index && args.length <= index + 3) {
			return getTabCompletionCoordinate(args, index, targetPos);
		}
		index += 3;
		if (args.length == index + 1) {
			return getListOfStringsMatchingLastWord(args, "replace", "masked", "filtered");
		} else if (args.length == index + 2) {
			return getListOfStringsMatchingLastWord(args, "normal", "force", "move");
		} else if (args.length == index + 3) {
			if ("filtered".equals(args[index])) {
				return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
			} else {
				return Collections.emptyList();
			}
		}
		return Collections.emptyList();
	}

}
