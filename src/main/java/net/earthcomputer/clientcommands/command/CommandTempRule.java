package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.ClientCommandsMod;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameRules;

public class CommandTempRule extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		// this code is very similar to CommandGameRule

		GameRules tempRules = ClientCommandsMod.INSTANCE.getTempRules();
		String rule = args.length > 0 ? args[0] : "";
		String value = args.length > 1 ? buildString(args, 1) : "";

		switch (args.length) {
		case 0:
			sender.sendMessage(new TextComponentString(joinNiceString(tempRules.getRules())));
			break;
		case 1:
			if (!tempRules.hasRule(rule)) {
				throw new CommandException("commands.gamerule.norule", rule);
			}
			value = tempRules.getString(rule);
			sender.sendMessage(new TextComponentString(rule).appendText(" = ").appendText(value));
			break;
		default:
			if (!tempRules.hasRule(rule)) {
				throw new CommandException("commands.gamerule.norule", rule);
			}
			if (tempRules.areSameType(rule, GameRules.ValueType.BOOLEAN_VALUE) && !"true".equals(value)
					&& !"false".equals(value)) {
				throw new CommandException("commands.generic.boolean.invalid", value);
			}
			tempRules.setOrCreateGameRule(rule, value);
			sender.sendMessage(new TextComponentTranslation("commands.gamerule.success", rule, value));
			break;
		}
	}

	@Override
	public String getName() {
		return "ctemprule";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/ctemprule [rule] [value]";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		GameRules tempRules = ClientCommandsMod.INSTANCE.getTempRules();
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, tempRules.getRules());
		} else if (args.length == 2 && tempRules.areSameType(args[0], GameRules.ValueType.BOOLEAN_VALUE)) {
			return getListOfStringsMatchingLastWord(args, "false", "true");
		} else {
			return Collections.emptyList();
		}
	}

}
