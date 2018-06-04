package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class CommandCFill extends CommandAreaExtension {

	@Override
	public String getName() {
		return "cfill";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cfill.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		sender = correctSender(sender);

		SelectionInfo selectionInfo = parseSelectionInfo(sender, args, 0);
		BlockPos pos1 = selectionInfo.from;
		BlockPos pos2 = selectionInfo.to;
		int index = selectionInfo.argLen;

		// Validation
		if (args.length <= index)
			throw new WrongUsageException(getUsage(sender));
		Block block = getBlockByText(sender, args[index]);
		IBlockState state;
		if (args.length >= index + 2) {
			state = convertArgToBlockState(block, args[index + 1]);
		} else {
			state = block.getDefaultState();
		}

		boolean hollow = false;
		boolean outline = false;

		if (args.length >= index + 3) {
			if ("replace".equals(args[index + 2]) && !block.hasTileEntity(state) && args.length >= index + 4) {
				Block replacedBlock = getBlockByText(sender, args[index + 3]);
				if (args.length >= index + 5)
					convertArgToBlockStatePredicate(replacedBlock, args[index + 4]);
			} else if ("hollow".equals(args[index + 2])) {
				hollow = true;
				args[index + 2] = "replace";
			} else if ("outline".equals(args[index + 2])) {
				outline = true;
				args[index + 2] = "replace";
			}
		}

		if (args.length >= index + 4 && block.hasTileEntity(state)) {
			try {
				JsonToNBT.getTagFromJson(buildString(args, index + 3));
			} catch (NBTException e) {
				throw new CommandException("commands.fill.tagError", e.getMessage());
			}
		}

		// Do the filling
		BlockPos from = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()),
				Math.min(pos1.getZ(), pos2.getZ()));
		BlockPos to = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()),
				Math.max(pos1.getZ(), pos2.getZ()));

		String[] extraArgs = Arrays.copyOfRange(args, index, args.length);
		if (!hollow || !outline || to.getX() - from.getX() < 2 || to.getY() - from.getY() < 2
				|| to.getZ() - from.getZ() < 2)
			areaExecute(from, to, extraArgs);
		else if (hollow || outline) {
			areaExecute(from, new BlockPos(to.getX(), from.getY(), to.getZ()), extraArgs);
			areaExecute(from, new BlockPos(from.getX(), to.getY(), to.getZ()), extraArgs);
			areaExecute(from, new BlockPos(to.getX(), to.getY(), from.getZ()), extraArgs);
			areaExecute(new BlockPos(to.getX(), from.getY(), from.getZ()), to, extraArgs);
			areaExecute(new BlockPos(from.getX(), from.getY(), to.getZ()), to, extraArgs);
			areaExecute(new BlockPos(from.getX(), to.getY(), from.getZ()), to, extraArgs);
			if (hollow) {
				extraArgs[0] = "air";
				areaExecute(from.add(1, 1, 1), to.add(-1, -1, -1), extraArgs);
			}
		}
	}

	@Override
	protected String makeCommand(BlockPos from, BlockPos to, Object[] extraArgs) {
		return String.format("/fill %s %s %s %s %s %s %s", from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(),
				to.getZ(), StringUtils.join(extraArgs, " "));
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
		if (args.length == index + 1) {
			return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
		} else if (args.length == index + 3) {
			return getListOfStringsMatchingLastWord(args, "replace", "destroy", "keep", "hollow", "outline");
		} else if (args.length == index + 4) {
			if ("replace".equals(args[index + 3])) {
				return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
			} else {
				return Collections.emptyList();
			}
		}
		return Collections.emptyList();
	}

}
