package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.earthcomputer.clientcommands.EnchantmentCracker;
import net.earthcomputer.clientcommands.EnchantmentCracker.EnchantManipulationStatus;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandCEnchant extends ClientCommandBase {

	@Override
	public String getName() {
		return "cenchant";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cenchant.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 4) {
			throw new WrongUsageException(getUsage(sender));
		}

		TaskManager.ensureNoTasks();

		Item item = getItemByText(sender, args[0]);

		Predicate<List<EnchantmentData>> enchantmentsPredicate = x -> true;
		for (int i = 1; i <= args.length - 3; i += 3) {
			Enchantment en;
			try {
				en = Enchantment.getEnchantmentByID(parseInt(args[i + 1], 0));
			} catch (NumberInvalidException e) {
				en = Enchantment.getEnchantmentByLocation(args[i + 1]);
			}
			if (en == null) {
				throw new CommandException("commands.cenchant.unknownEnchantment", args[i + 1]);
			}
			Enchantment enchantment = en;

			String strLevel = args[i + 2];
			int minLevel, maxLevel;
			if (strLevel.contains("..")) {
				String[] parts = strLevel.split("\\.\\.", 2);
				minLevel = parseInt(parts[0], 1, enchantment.getMaxLevel());
				maxLevel = parseInt(parts[1], minLevel, enchantment.getMaxLevel());
			} else {
				minLevel = maxLevel = parseInt(strLevel, 1, enchantment.getMaxLevel());
			}

			Predicate<EnchantmentData> predicate = ench -> ench.enchantment == enchantment
					&& ench.enchantmentLevel >= minLevel && ench.enchantmentLevel <= maxLevel;
			if ("with".equals(args[i])) {
				enchantmentsPredicate = enchantmentsPredicate.and(list -> list.stream().anyMatch(predicate));
			} else if ("without".equals(args[i])) {
				enchantmentsPredicate = enchantmentsPredicate.and(list -> list.stream().noneMatch(predicate));
			} else {
				throw new WrongUsageException(getUsage(sender));
			}
		}

		EnchantManipulationStatus status = EnchantmentCracker.manipulateEnchantments(item, enchantmentsPredicate);
		if (status == EnchantManipulationStatus.OK) {
			sender.sendMessage(new TextComponentTranslation("commands.cenchant.success"));
		} else {
			throw new CommandException(status.getTranslation());
		}

	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 0) {
			return Collections.emptyList();
		}
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
		} else {
			switch (args.length % 3) {
			case 2:
				return getListOfStringsMatchingLastWord(args, "with", "without");
			case 0:
				Item item;
				try {
					item = getItemByText(sender, args[0]);
				} catch (NumberInvalidException e) {
					return Collections.emptyList();
				}
				ItemStack stack = new ItemStack(item);
				List<Enchantment> applicableEnchantments = new ArrayList<>();
				for (Enchantment ench : Enchantment.REGISTRY) {
					if (!ench.isTreasureEnchantment()) {
						if (ench.canApplyAtEnchantingTable(stack) || (item == Items.BOOK && ench.isAllowedOnBooks())) {
							applicableEnchantments.add(ench);
						}
					}
				}
				return getListOfStringsMatchingLastWord(args, applicableEnchantments.stream()
						.map(Enchantment.REGISTRY::getNameForObject).collect(Collectors.toList()));
			default:
				return Collections.emptyList();
			}
		}
	}

}
