package net.earthcomputer.clientcommands.command;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.earthcomputer.clientcommands.ClientCommandsMod;
import net.earthcomputer.clientcommands.LongTask;
import net.earthcomputer.clientcommands.SimulatedWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class CommandSimGen extends ClientCommandBase {

	private static final Pattern GENERATOR_ARG_PATTERN = Pattern.compile("([\\w\\$\\.]+)(?:\\(([-+\\w,\\.]*)\\))?");

	@Override
	public String getName() {
		return "csimgen";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/csimgen <generator> <times> [<x> <y> <z>]";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(sender));
		}

		ClientCommandsMod.INSTANCE.ensureNoTasks();

		String generatorArg = args[0];
		Matcher matcher = GENERATOR_ARG_PATTERN.matcher(generatorArg);
		if (!matcher.matches()) {
			throw new CommandException("Invalid generator arg syntax");
		}

		String generatorName = matcher.group(1);
		Class<?> generatorClass;
		try {
			generatorClass = Class.forName(generatorName);
		} catch (ClassNotFoundException e) {
			throw new CommandException("Class not found: " + generatorName);
		}
		if (!WorldGenerator.class.isAssignableFrom(generatorClass)) {
			throw new CommandException("Class " + generatorName + " is not a world generator");
		}

		String argsSection = matcher.group(2);
		Object[] ctorArgs;
		Class<?>[] ctorTypes;
		if (argsSection == null || argsSection.isEmpty()) {
			ctorArgs = new Object[0];
			ctorTypes = new Class[0];
		} else {
			String[] parts = argsSection.split(",");
			ctorArgs = new Object[parts.length];
			ctorTypes = new Class[parts.length];
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i].toLowerCase(Locale.ENGLISH);
				if ("true".equals(part)) {
					ctorArgs[i] = Boolean.TRUE;
					ctorTypes[i] = boolean.class;
				} else if ("false".equals(part)) {
					ctorArgs[i] = Boolean.FALSE;
					ctorTypes[i] = boolean.class;
				} else if (part.endsWith("b")) {
					try {
						ctorArgs[i] = Byte.parseByte(part.substring(0, part.length() - 1));
						ctorTypes[i] = byte.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid byte: " + part);
					}
				} else if (part.endsWith("d")) {
					try {
						ctorArgs[i] = Double.parseDouble(part.substring(0, part.length() - 1));
						ctorTypes[i] = double.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid double: " + part);
					}
				} else if (part.endsWith("f")) {
					try {
						ctorArgs[i] = Float.parseFloat(part.substring(0, part.length() - 1));
						ctorTypes[i] = float.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid float: " + part);
					}
				} else if (part.endsWith("i")) {
					try {
						ctorArgs[i] = Integer.parseInt(part.substring(0, part.length() - 1));
						ctorTypes[i] = int.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid int: " + part);
					}
				} else if (part.endsWith("l")) {
					try {
						ctorArgs[i] = Long.parseLong(part.substring(0, part.length() - 1));
						ctorTypes[i] = long.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid long: " + part);
					}
				} else if (part.endsWith("s")) {
					try {
						ctorArgs[i] = Short.parseShort(part.substring(0, part.length() - 1));
						ctorTypes[i] = short.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid short: " + part);
					}
				} else if (part.contains(".") || part.contains("e")) {
					try {
						ctorArgs[i] = Double.parseDouble(part);
						ctorTypes[i] = double.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid double: " + part);
					}
				} else {
					try {
						ctorArgs[i] = Integer.parseInt(part);
						ctorTypes[i] = int.class;
					} catch (NumberFormatException e) {
						throw new CommandException("Invalid int: " + part);
					}
				}
			}
		}

		WorldGenerator generatorInstance;
		try {
			generatorInstance = (WorldGenerator) generatorClass.getConstructor(ctorTypes).newInstance(ctorArgs);
		} catch (Exception e) {
			List<Constructor<?>> ctors = new ArrayList<>();
			if (!Modifier.isAbstract(generatorClass.getModifiers())) {
				Collections.addAll(ctors, generatorClass.getConstructors());
			}
			if (ctors.isEmpty()) {
				sender.sendMessage(
						new TextComponentString(TextFormatting.RED + "No constructors for " + generatorName));
			} else {
				sender.sendMessage(
						new TextComponentString(TextFormatting.RED + "Constructors for " + generatorName + ":"));
				for (Constructor<?> ctor : ctors) {
					sender.sendMessage(
							new TextComponentString(TextFormatting.RED + "(" + Arrays.stream(ctor.getParameterTypes())
									.map(Class::getSimpleName).collect(Collectors.joining(",")) + ")"));
				}
			}
			throw new CommandException("Failed to instantiate " + generatorName + " with those arguments");
		}

		int tries = parseInt(args[1], 1, 1000000);
		int tenPercentInterval = tries / 10;

		BlockPos position = args.length > 2 ? parseBlockPos(sender, args, 2, true) : sender.getPosition();

		Thread thread = new Thread(() -> {
			SimulatedWorld world = new SimulatedWorld(Minecraft.getMinecraft().world);
			Random rand = new Random();
			List<BlockPos> positions = new ArrayList<>();
			Map<IBlockState, Integer> addedBlocksCount = new LinkedHashMap<>();
			Map<IBlockState, Integer> removedBlocksCount = new LinkedHashMap<>();
			Map<Class<? extends Entity>, Integer> addedEntities = new LinkedHashMap<>();
			int failedAttempts = 0;

			for (int i = 0; i < tries; i++) {
				if (Thread.interrupted()) {
					return;
				}

				if (tenPercentInterval >= 1000 && i % tenPercentInterval == 0) {
					sender.sendMessage(new TextComponentString(
							TextFormatting.GOLD + "Simulating: " + (i / tenPercentInterval * 10) + "%"));
				}

				if (!generatorInstance.generate(world, rand, position)) {
					failedAttempts++;
					continue;
				}
				Map<BlockPos, IBlockState> addedBlocks = world.getChangedBlocks();
				positions.clear();
				positions.addAll(addedBlocks.keySet());
				for (IBlockState state : addedBlocks.values()) {
					addedBlocksCount.put(state, addedBlocksCount.getOrDefault(state, 0) + 1);
				}
				for (Entity ent : world.getNewEntities()) {
					Class<? extends Entity> c = ent.getClass();
					addedEntities.put(c, addedEntities.getOrDefault(c, 0) + 1);
				}
				world.revert();
				for (BlockPos pos : positions) {
					IBlockState state = world.getBlockState(pos);
					removedBlocksCount.put(state, removedBlocksCount.getOrDefault(state, 0) + 1);
				}
			}

			sender.sendMessage(new TextComponentString("" + TextFormatting.YELLOW + TextFormatting.BOLD + "Simulated "
					+ generatorName + " " + tries + " times"));
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "Failed attempts: " + failedAttempts));
			sender.sendMessage(
					new TextComponentString(TextFormatting.GREEN + "Successful attempts: " + (tries - failedAttempts)));
			sender.sendMessage(new TextComponentString(TextFormatting.BOLD + "Added blocks:"));
			if (addedBlocksCount.isEmpty()) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "None"));
			} else {
				for (Map.Entry<IBlockState, Integer> addedBlock : addedBlocksCount.entrySet()) {
					sender.sendMessage(new TextComponentString("" + TextFormatting.YELLOW + addedBlock.getKey() + ": "
							+ TextFormatting.RESET + addedBlock.getValue() + TextFormatting.GRAY + " total, "
							+ TextFormatting.RESET + String.format("%.3f", (double) addedBlock.getValue() / tries)
							+ TextFormatting.GRAY + " average" + TextFormatting.RESET));
				}
			}
			sender.sendMessage(new TextComponentString(TextFormatting.BOLD + "Removed blocks:"));
			if (removedBlocksCount.isEmpty()) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "None"));
			} else {
				for (Map.Entry<IBlockState, Integer> removedBlock : removedBlocksCount.entrySet()) {
					sender.sendMessage(new TextComponentString("" + TextFormatting.YELLOW + removedBlock.getKey() + ": "
							+ TextFormatting.RESET + removedBlock.getValue() + TextFormatting.GRAY + " total, "
							+ TextFormatting.RESET + String.format("%.3f", (double) removedBlock.getValue() / tries)
							+ TextFormatting.GRAY + " average" + TextFormatting.RESET));
				}
			}
			sender.sendMessage(new TextComponentString(TextFormatting.BOLD + "Added entities:"));
			if (addedEntities.isEmpty()) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "None"));
			} else {
				for (Map.Entry<Class<? extends Entity>, Integer> addedEntity : addedEntities.entrySet()) {
					sender.sendMessage(new TextComponentString("" + TextFormatting.YELLOW
							+ EntityList.getKey(addedEntity.getKey()) + ": " + TextFormatting.RESET
							+ addedEntity.getValue() + TextFormatting.GRAY + " total, " + TextFormatting.RESET
							+ String.format("%.3f", (double) addedEntity.getValue() / tries) + TextFormatting.GRAY
							+ " average" + TextFormatting.RESET));
				}
			}
		});

		ClientCommandsMod.INSTANCE.addLongTask(new LongTask() {
			@Override
			protected void taskTick() {
				if (!thread.isAlive()) {
					setFinished();
				}
			}

			@Override
			public void start() {
				thread.start();
			}

			@Override
			public void cleanup() {
				if (thread.isAlive()) {
					thread.interrupt();
				}
			}

			@Override
			protected int getTimeout() {
				return Integer.MAX_VALUE;
			}
		});
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			Map<String, Class<?>> cachedClasses = ReflectionHelper.getPrivateValue(LaunchClassLoader.class,
					Launch.classLoader, "cachedClasses");
			List<String> worldGenClasses = cachedClasses.values().stream()
					.filter(WorldGenerator.class::isAssignableFrom).map(Class::getName).sorted()
					.collect(Collectors.toList());
			return getListOfStringsMatchingLastWord(args, worldGenClasses);
		} else if (args.length >= 3 && args.length <= 5) {
			return getTabCompletionCoordinate(args, 2, targetPos);
		} else {
			return Collections.emptyList();
		}
	}

}
