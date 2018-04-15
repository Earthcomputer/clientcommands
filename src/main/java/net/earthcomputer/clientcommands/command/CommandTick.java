package net.earthcomputer.clientcommands.command;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.EventManager;
import net.earthcomputer.clientcommands.EventManager.Listener;
import net.earthcomputer.clientcommands.network.PacketEvent;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

public class CommandTick extends ClientCommandBase {

	private static final DecimalFormat DEC_FMT = new DecimalFormat("0.00");

	@Override
	public String getName() {
		return "ctick";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.ctick.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(sender));
		}

		switch (args[0]) {
		case "client":
			ctickClient(sender, args);
			break;
		case "server":
			if (Minecraft.getMinecraft().isIntegratedServerRunning())
				ctickIntegratedServer(sender, args);
			else
				ctickServer(sender, args);
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private void ctickClient(ICommandSender sender, String[] args) throws CommandException {
		switch (args[1]) {
		case "tps":
		case "mspt":
			TaskManager.ensureNoTasks();
			TickMeasuringTask measurer = new TickMeasuringTask(sender, "tps".equals(args[1]), false);
			TaskManager.addLongTask(measurer);
			EventManager.addTickListener(new Listener<ClientTickEvent>() {
				@Override
				public void accept(ClientTickEvent e) {
					measurer.startTick();
				}

				@Override
				public boolean wasFinalAction() {
					return measurer.isFinished();
				}
			});
			EventManager.addEndTickListener(new Listener<ClientTickEvent>() {
				@Override
				public void accept(ClientTickEvent e) {
					measurer.endTick();
				}

				@Override
				public boolean wasFinalAction() {
					return measurer.isFinished();
				}
			});
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private void ctickIntegratedServer(ICommandSender sender, String[] args) throws CommandException {
		switch (args[1]) {
		case "tps":
		case "mspt":
			TaskManager.ensureNoTasks();
			TickMeasuringTask measurer = new TickMeasuringTask(sender, "tps".equals(args[1]), false);
			TaskManager.addLongTask(measurer);
			EventManager.addServerTickListener(new Listener<ServerTickEvent>() {
				@Override
				public void accept(ServerTickEvent e) {
					measurer.startTick();
				}

				@Override
				public boolean wasFinalAction() {
					return measurer.isFinished();
				}
			});
			EventManager.addServerEndTickListener(new Listener<ServerTickEvent>() {
				@Override
				public void accept(ServerTickEvent e) {
					measurer.endTick();
				}

				@Override
				public boolean wasFinalAction() {
					return measurer.isFinished();
				}
			});
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private void ctickServer(ICommandSender sender, String[] args) throws CommandException {
		switch (args[1]) {
		case "tps":
		case "mspt":
			TaskManager.ensureNoTasks();
			TickMeasuringTask measurer = new TickMeasuringTask(sender, "tps".equals(args[1]), true);
			TaskManager.addLongTask(measurer);
			EventManager.addInboundPacketPreListener(new Listener<PacketEvent.Inbound.Pre>() {
				long lastTick = -1;

				@Override
				public void accept(PacketEvent.Inbound.Pre e) {
					if (e.getPacket() instanceof SPacketTimeUpdate) {
						long tick = ((SPacketTimeUpdate) e.getPacket()).getTotalWorldTime();
						if (lastTick != -1) {
							int deltaTick = (int) (tick - lastTick);
							measurer.incrTickCount(deltaTick);
						}
						lastTick = tick;
					}
				}

				@Override
				public boolean wasFinalAction() {
					return measurer.isFinished();
				}
			});
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
			return getListOfStringsMatchingLastWord(args, "client", "server");
		} else if (args.length == 2) {
			return getListOfStringsMatchingLastWord(args, "tps", "mspt");
		} else {
			return Collections.emptyList();
		}
	}

	private static class TickMeasuringTask extends LongTask {

		private static final int PERIOD = 100;

		private int tickCount = 0;
		private long totalTickTime = 0;
		private long startTickTime;
		private boolean hadFirstTick = false;
		private long firstTickStart;
		private long lastTickStart;

		private ICommandSender sender;
		private boolean tps;
		private boolean forceInaccurate;

		public TickMeasuringTask(ICommandSender sender, boolean tps, boolean forceInaccurate) {
			this.sender = sender;
			this.tps = tps;
			this.forceInaccurate = forceInaccurate;
		}

		public void incrTickCount(int count) {
			if (!hadFirstTick) {
				firstTickStart = System.nanoTime();
				hadFirstTick = true;
			} else {
				tickCount += count;
			}
		}

		public void startTick() {
			startTickTime = System.nanoTime();
			if (!hadFirstTick) {
				firstTickStart = startTickTime;
				hadFirstTick = true;
			}
		}

		public void endTick() {
			if (hadFirstTick) {
				totalTickTime += System.nanoTime() - startTickTime;
				tickCount++;
			}
		}

		@Override
		protected void taskTick() {
			if (tickCount >= PERIOD) {
				lastTickStart = System.nanoTime();
				setFinished();
			}
		}

		@Override
		public void start() {
			sender.sendMessage(new TextComponentTranslation("commands.ctick.measuring"));
		}

		@Override
		public void cleanup() {
			if (tps) {
				long totalTime = lastTickStart - firstTickStart;
				double tps = 1000000000D * tickCount / totalTime;
				sender.sendMessage(new TextComponentTranslation("commands.ctick.tps",
						totalTime == 0 ? "Immeasurable" : DEC_FMT.format(tps)));
			} else if (forceInaccurate) {
				long totalTime = lastTickStart - firstTickStart;
				double mspt = totalTime / (1000000D * tickCount);
				sender.sendMessage(new TextComponentTranslation("commands.ctick.mspt", DEC_FMT.format(mspt)));
				sender.sendMessage(new TextComponentTranslation("commands.ctick.mspt.inaccurate"));
			} else {
				double mspt = totalTickTime / (1000000D * tickCount);
				sender.sendMessage(new TextComponentTranslation("commands.ctick.mspt", DEC_FMT.format(mspt)));
			}
		}

		@Override
		public int getTimeout() {
			return 1200;
		}
	}

}
