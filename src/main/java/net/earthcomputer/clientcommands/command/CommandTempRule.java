package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandTempRule extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			throw new WrongUsageException(getUsage(sender));
		}

		String mode = args[0];

		if ("list".equals(mode)) {
			sender.sendMessage(
					new TextComponentTranslation("commands.ctemprule.list.success", TempRules.getRuleNames().size()));
			TempRules.getRuleNames().forEach(name -> sender.sendMessage(new TextComponentString("- " + name)));
		} else {
			if (!"get".equals(mode) && !"set".equals(mode) && !"reset".equals(mode)) {
				throw new WrongUsageException(getUsage(sender));
			}
			if (args.length == 1) {
				if ("set".equals(mode)) {
					throw new WrongUsageException("commands.ctemprule.set.usage");
				} else {
					throw new WrongUsageException("commands.ctemprule." + mode + ".usage");
				}
			}

			String ruleName = args[1];
			if (!TempRules.hasRule(ruleName)) {
				throw new CommandException("commands.ctemprule.unknownRule", ruleName);
			}
			TempRules.Rule<?> rule = TempRules.getRule(ruleName);

			switch (mode) {
			case "get":
				sender.sendMessage(new TextComponentTranslation("commands.ctemprule.get.success", ruleName,
						ruleValToString(rule)));
				break;
			case "set":
				if (args.length == 2) {
					throw new WrongUsageException("commands.ctemprule.set.usage");
				}
				if (rule.isReadOnly()) {
					throw new CommandException("commands.ctemprule.readOnly", ruleName);
				}
				String strVal = buildString(args, 2);
				setRuleVal(rule, strVal);
				sender.sendMessage(new TextComponentTranslation("commands.ctemprule.set.success", ruleName, strVal));
				break;
			case "reset":
				if (rule.isReadOnly()) {
					throw new CommandException("commands.ctemprule.readOnly", ruleName);
				}
				rule.setToDefault();
				sender.sendMessage(new TextComponentTranslation("commands.ctemprule.reset.success", ruleName,
						ruleValToString(rule)));
				break;
			}
		}
	}

	private static <T> String ruleValToString(TempRules.Rule<T> rule) {
		return rule.getDataType().toString(rule.getValue());
	}

	private static <T> void setRuleVal(TempRules.Rule<T> rule, String strVal) throws CommandException {
		rule.setValue(rule.getDataType().parse(strVal));
	}

	@Override
	public String getName() {
		return "ctemprule";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.ctemprule.usage";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "get", "set", "reset", "list");
		} else if (args.length == 2 && !"list".equals(args[0])) {
			if ("get".equals(args[0])) {
				return getListOfStringsMatchingLastWord(args, TempRules.getRuleNames());
			} else {
				return getListOfStringsMatchingLastWord(args,
						TempRules.getRules().stream().filter(rule -> !rule.isReadOnly()).map(TempRules.Rule::getName)
								.sorted().collect(Collectors.toList()));
			}
		} else if (args.length == 3 && "set".equals(args[0]) && TempRules.hasRule(args[1])) {
			TempRules.Rule<?> rule = TempRules.getRule(args[1]);
			if (!rule.isReadOnly()) {
				return getListOfStringsMatchingLastWord(args, rule.getDataType().getTabCompletionOptions());
			}
		}
		return Collections.emptyList();
	}

}
