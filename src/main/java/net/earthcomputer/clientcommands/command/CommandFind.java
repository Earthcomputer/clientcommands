package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class CommandFind extends ClientCommandBase {

	private static final ResourceLocation PLAYER = new ResourceLocation("player");

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length % 2 == 0) {
			throw new WrongUsageException(getUsage(sender));
		}

		World world = Minecraft.getMinecraft().world;
		EntityPlayer player = Minecraft.getMinecraft().player;

		// create a stream, and filter it with the arguments
		Stream<Entity> entities = world.loadedEntityList.stream();

		// type
		ResourceLocation type = new ResourceLocation(args[0]);
		Class<? extends Entity> entityClass;
		if (PLAYER.equals(type)) {
			entityClass = EntityPlayer.class;
		} else {
			entityClass = EntityList.getClass(type);
		}
		if (entityClass == null) {
			throw new CommandException("Unknown entity type " + args[0]);
		}
		entities = entities.filter(entityClass::isInstance);

		// remaining filter arguments
		for (int i = 1; i < args.length; i += 2) {
			String arg = args[i];
			String value = args[i + 1];

			switch (arg) {
			case "rmin":
				int rmin = parseInt(value);
				entities = entities.filter(entity -> player.getDistanceSq(entity) >= rmin * rmin);
				break;
			case "rmax":
				int rmax = parseInt(value);
				entities = entities.filter(entity -> player.getDistanceSq(entity) <= rmax * rmax);
				break;
			case "order":
				switch (value) {
				case "nearest":
					entities = entities.sorted(Comparator.comparingDouble(entity -> player.getDistanceSq(entity)));
					break;
				case "furthest":
					entities = entities.sorted(
							Comparator.<Entity>comparingDouble(entity -> player.getDistanceSq(entity)).reversed());
					break;
				case "random":
					int r = ThreadLocalRandom.current().nextInt();
					Comparator<Entity> randomComparator = Comparator
							.<Entity>comparingInt(entity -> entity.getUniqueID().hashCode() ^ r)
							.thenComparing(Entity::getUniqueID);
					entities = entities.sorted(randomComparator);
					break;
				default:
					throw new CommandException("Unknown arg value for order: " + value);
				}
				break;
			case "limit":
				int limit = parseInt(value, 0);
				entities = entities.limit(limit);
				break;
			case "name":
				entities = entities.filter(entity -> entity.getName().equalsIgnoreCase(value));
				break;
			default:
				throw new CommandException("Unknown argument " + arg);
			}
		}

		// compile to list
		List<Entity> entityList = entities.collect(Collectors.toList());

		// output list
		if (entityList.isEmpty()) {
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "No entities matched your query"));
		} else {
			sender.sendMessage(
					new TextComponentString(String.format("%d entities matched your query", entityList.size())));
			for (Entity entity : entityList) {
				sender.sendMessage(new TextComponentString(String.format("Found %s at ", entity.getName()))
						.appendSibling(getCoordsTextComponent(new BlockPos(entity)))
						.appendSibling(new TextComponentString(
								String.format(", %.2f blocks away", player.getDistance(entity)))));
			}
		}
	}

	@Override
	public String getName() {
		return "cfind";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/cfind <type> [(<arg> <value>)...]";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 0) {
			return Collections.emptyList();
		}
		if (args.length == 1) {
			List<ResourceLocation> list = new ArrayList<>();
			list.add(PLAYER);
			list.addAll(EntityList.getEntityNameList());
			list.sort(Comparator.comparing(ResourceLocation::getResourceDomain)
					.thenComparing(ResourceLocation::getResourcePath));
			return getListOfStringsMatchingLastWord(args, list);
		}
		if (args.length % 2 == 0) {
			return getListOfStringsMatchingLastWord(args, "rmin", "rmax", "order", "limit", "name");
		} else {
			switch (args[args.length - 2]) {
			case "order":
				return getListOfStringsMatchingLastWord(args, "nearest", "furthest", "random");
			case "name":
				return getListOfStringsMatchingLastWord(args, Minecraft.getMinecraft().world.playerEntities.stream()
						.map(Entity::getName).sorted().collect(Collectors.toList()));
			default:
				return Collections.emptyList();
			}
		}
	}

}
