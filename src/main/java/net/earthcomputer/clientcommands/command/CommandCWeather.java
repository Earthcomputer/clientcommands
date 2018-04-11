package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.EventManager;
import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;

public class CommandCWeather extends ClientCommandBase {

	private static float mockingRainStrength;
	private static float mockingThunderStrength;
	private static float serverSideRainStrength;
	private static float serverSideThunderStrength;

	static {
		EventManager.addInboundPacketPreListener(e -> {
			if (TempRules.MOCKING_WEATHER.getValue()) {
				if (e.getPacket() instanceof SPacketChangeGameState) {
					SPacketChangeGameState packet = (SPacketChangeGameState) e.getPacket();
					int id = packet.getGameState();
					switch (id) {
					case 1:
					case 2:
						e.setCanceled(true);
						break;
					case 7:
						serverSideRainStrength = packet.getValue();
						e.setCanceled(true);
						break;
					case 8:
						serverSideThunderStrength = packet.getValue();
						e.setCanceled(true);
						break;
					}
				}
			}
		});
		EventManager.addConnectListener(e -> {
			if (TempRules.MOCKING_WEATHER.getValue()) {
				System.out.println("---------");
				System.out.println(mockingRainStrength);
				System.out.println(mockingThunderStrength);
				World world = Minecraft.getMinecraft().world;
				WorldInfo worldInfo = world.getWorldInfo();
				world.setRainStrength(mockingRainStrength);
				world.setThunderStrength(mockingThunderStrength);
				worldInfo.setRaining(mockingRainStrength != 0);
				worldInfo.setThundering(mockingThunderStrength != 0);
			}
		});
	}

	@Override
	public String getName() {
		return "cweather";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cweather.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		String subcommand = args.length == 0 ? "info" : args[0];

		World world = Minecraft.getMinecraft().world;
		WorldInfo worldInfo = world.getWorldInfo();

		switch (subcommand) {
		case "mock":
			if (args.length < 2) {
				throw new WrongUsageException("commands.cweather.mock.usage");
			}
			switch (args[1]) {
			case "clear":
				if (!TempRules.MOCKING_WEATHER.getValue()) {
					serverSideRainStrength = world.getRainStrength(1);
					serverSideThunderStrength = world.getThunderStrength(1);
				}
				world.setRainStrength(0);
				world.setThunderStrength(0);
				worldInfo.setRaining(false);
				worldInfo.setThundering(false);
				sender.sendMessage(new TextComponentTranslation("commands.cweather.mock.clear.success"));
				break;
			case "rain":
				if (!TempRules.MOCKING_WEATHER.getValue()) {
					serverSideRainStrength = world.getRainStrength(1);
					serverSideThunderStrength = world.getThunderStrength(1);
				}
				float strength = args.length < 3 ? 1 : (float) parseDouble(args[2], 0, 1);
				world.setRainStrength(strength);
				world.setThunderStrength(0);
				worldInfo.setRaining(true);
				worldInfo.setThundering(false);
				sender.sendMessage(new TextComponentTranslation("commands.cweather.mock.rain.success"));
				break;
			case "thunder":
				if (!TempRules.MOCKING_WEATHER.getValue()) {
					serverSideRainStrength = world.getRainStrength(1);
					serverSideThunderStrength = world.getThunderStrength(1);
				}
				strength = args.length < 3 ? 1 : (float) parseDouble(args[2], 0, 1);
				world.setRainStrength(strength);
				world.setThunderStrength(strength);
				worldInfo.setRaining(true);
				worldInfo.setThundering(true);
				sender.sendMessage(new TextComponentTranslation("commands.cweather.mock.thunder.success"));
				break;
			default:
				throw new WrongUsageException("commands.cweather.mock.usage");
			}
			TempRules.MOCKING_WEATHER.setValue(Boolean.TRUE);
			mockingRainStrength = world.getRainStrength(1);
			mockingThunderStrength = world.getThunderStrength(1);
			break;
		case "unmock":
			TempRules.MOCKING_WEATHER.setValue(Boolean.FALSE);
			world.setRainStrength(serverSideRainStrength);
			world.setThunderStrength(serverSideThunderStrength);
			worldInfo.setRaining(serverSideRainStrength != 0);
			worldInfo.setThundering(serverSideThunderStrength != 0);
			sender.sendMessage(new TextComponentTranslation("commands.cweather.unmock.success"));
			break;
		case "info":
			if (world.getThunderStrength(1) != 0) {
				sender.sendMessage(
						new TextComponentTranslation("commands.cweather.info.thunder", world.getThunderStrength(1)));
			} else if (world.getRainStrength(1) != 0) {
				sender.sendMessage(
						new TextComponentTranslation("commands.cweather.info.rain", world.getRainStrength(1)));
			} else {
				sender.sendMessage(new TextComponentTranslation("commands.cweather.info.clear"));
			}
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 0) {
			return Collections.emptyList();
		} else if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "mock", "unmock", "info");
		} else if ("mock".equals(args[0]) && args.length == 2) {
			return getListOfStringsMatchingLastWord(args, "clear", "rain", "thunder");
		} else {
			return Collections.emptyList();
		}
	}

}
