package net.earthcomputer.clientcommands.command;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.EventManager;
import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class CommandCTime extends ClientCommandBase {

	static {
		EventManager.addInboundPacketPreListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof SPacketTimeUpdate) {
				if (TempRules.MOCKING_TIME.getValue()) {
					e.setCanceled(true);
				}
			}
		});
	}

	@Override
	public String getName() {
		return "ctime";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.ctime.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			args = new String[] { "daytime" };
		}

		World world = Minecraft.getMinecraft().world;

		List<String> lines = new ArrayList<>();

		switch (args[0]) {
		case "daytime": {
			int daytime = (int) (world.getWorldTime() % 24000);
			lines.add(I18n.format("commands.ctime.daytime", daytime,
					LocalTime.of(daytime / 1000, (daytime % 1000) * 60 / 1000)
							.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
			break;
		}
		case "day": {
			int day = (int) ((world.getWorldTime() / 24000) % Integer.MAX_VALUE);
			lines.add(I18n.format("commands.ctime.day", day));
			break;
		}
		case "gametime": {
			int gametime = (int) (world.getTotalWorldTime() % Integer.MAX_VALUE);
			lines.add(I18n.format("commands.ctime.gametime", gametime, StatBase.timeStatType.format(gametime)));
			break;
		}
		case "info": {
			int day = (int) ((world.getWorldTime() / 24000) % Integer.MAX_VALUE);
			int daytime = (int) (world.getWorldTime() % 24000);
			int gametime = (int) (world.getTotalWorldTime() % Integer.MAX_VALUE);
			int moonPhase = day % 8;
			int skylightSubtracted = world.calculateSkylightSubtracted(1);

			lines.add(I18n.format("commands.ctime.infoHeader"));
			lines.add(I18n.format("commands.ctime.daytime", daytime,
					LocalTime.of((daytime / 1000 + 6) % 24, (daytime % 1000) * 60 / 1000)
							.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
			lines.add(I18n.format("commands.ctime.day", day));
			lines.add(I18n.format("commands.ctime.gametime", gametime, StatBase.timeStatType.format(gametime)));
			lines.add(I18n.format("commands.ctime.moon", moonPhase, I18n.format("commands.ctime.moon." + moonPhase)));
			lines.add(I18n.format("commands.ctime.mobsBurn", formatBoolean(skylightSubtracted < 4)));
			lines.add(I18n.format("commands.ctime.mobsSpawn", formatBoolean(15 - skylightSubtracted <= 7)));
			lines.add(I18n.format("commands.ctime.doDaylightCycle",
					formatBoolean(world.getGameRules().getBoolean("doDaylightCycle"))));
			break;
		}
		case "mock": {
			if (args.length < 2) {
				throw new WrongUsageException("commands.ctime.mock.usage");
			}
			int daytime;
			if ("day".equals(args[1])) {
				daytime = 1000;
			} else if ("night".equals(args[1])) {
				daytime = 13000;
			} else if ("noon".equals(args[1])) {
				daytime = 6000;
			} else if ("midnight".equals(args[1])) {
				daytime = 18000;
			} else {
				daytime = parseInt(args[1], 0);
			}
			lines.add(I18n.format("commands.ctime.mock.success", daytime));
			boolean doDaylightCycle = args.length < 3 ? true : Boolean.parseBoolean(args[2]);
			if (!doDaylightCycle) {
				daytime = -daytime;
				if (daytime == 0) {
					daytime = -1;
				}
			}
			TempRules.MOCKING_TIME.setValue(Boolean.TRUE);
			world.setWorldTime(daytime);
			break;
		}
		case "unmock": {
			TempRules.MOCKING_TIME.setValue(Boolean.FALSE);
			lines.add(I18n.format("commands.ctime.unmock.success"));
			break;
		}
		default:
			throw new WrongUsageException(getUsage(sender));
		}

		for (String line : lines) {
			sender.sendMessage(new TextComponentString(line));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "daytime", "day", "gametime", "info", "mock", "unmock");
		} else if (args.length != 0 && "mock".equals(args[0])) {
			if (args.length == 2) {
				return getListOfStringsMatchingLastWord(args, "day", "night", "noon", "midnight");
			} else if (args.length == 3) {
				return getListOfStringsMatchingLastWord(args, "false", "true");
			}
		}
		return Collections.emptyList();
	}

	private static String formatBoolean(boolean b) {
		if (b) {
			return TextFormatting.GREEN + "true";
		} else {
			return TextFormatting.RED + "false";
		}
	}

}
