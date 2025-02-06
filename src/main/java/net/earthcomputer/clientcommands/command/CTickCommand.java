package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.event.MoreClientEvents;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.TimeUtil;

import java.text.DecimalFormat;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTickCommand {

    private static final DecimalFormat DEC_FMT = new DecimalFormat("0.00");

    private static final String TASK_NAME = "ctick";

    private static CurrentTask currentTask = null;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctick")
            .then(literal("client")
                .then(literal("tps")
                    .executes(ctx -> getClientTps(ctx.getSource())))
                .then(literal("mspt")
                    .executes(ctx -> getClientMspt(ctx.getSource()))))
            .then(literal("server")
                .then(literal("tps")
                    .executes(ctx -> getServerTps(ctx.getSource())))
                .then(literal("mspt")
                    .executes(ctx -> getServerMspt(ctx.getSource())))));
    }

    private static int getClientTps(FabricClientCommandSource source) throws CommandSyntaxException {
        stopPreviousTask();

        TickMeasuringTask measurer = new TickMeasuringTask(true, false);
        String name = TaskManager.addTask(TASK_NAME, measurer);
        currentTask = new CurrentTask(name, measurer);

        float tps = source.getWorld().tickRateManager().tickrate();
        source.sendFeedback(Component.translatable("commands.ctick.client.tps.expectedTps", tps));
        return (int) tps;
    }

    private static int getClientMspt(FabricClientCommandSource source) throws CommandSyntaxException {
        stopPreviousTask();

        TickMeasuringTask measurer = new TickMeasuringTask(false, false);
        String name = TaskManager.addTask(TASK_NAME, measurer);
        currentTask = new CurrentTask(name, measurer);

        float mspt = TimeUtil.MILLISECONDS_PER_SECOND / source.getWorld().tickRateManager().tickrate();
        source.sendFeedback(Component.translatable("commands.ctick.client.mspt.expectedMspt", mspt));
        return (int) mspt;
    }

    private static int getServerTps(FabricClientCommandSource source) throws CommandSyntaxException {
        stopPreviousTask();

        boolean isIntegratedServer = source.getClient().hasSingleplayerServer();
        TickMeasuringTask measurer = new TickMeasuringTask(true, !isIntegratedServer);
        String name = TaskManager.addTask(TASK_NAME, measurer);
        currentTask = new CurrentTask(name, measurer);

        float tps = source.getWorld().tickRateManager().tickrate();
        source.sendFeedback(Component.translatable("commands.ctick.server.tps.expectedTps", tps));
        return (int) tps;
    }

    private static int getServerMspt(FabricClientCommandSource source) throws CommandSyntaxException {
        stopPreviousTask();

        boolean isIntegratedServer = source.getClient().hasSingleplayerServer();
        TickMeasuringTask measurer = new TickMeasuringTask(false, !isIntegratedServer);
        String name = TaskManager.addTask(TASK_NAME, measurer);
        currentTask = new CurrentTask(name, measurer);

        float mspt = TimeUtil.MILLISECONDS_PER_SECOND / source.getWorld().tickRateManager().tickrate();
        source.sendFeedback(Component.translatable("commands.ctick.server.mspt.expectedMspt", mspt));
        return (int) mspt;
    }

    private static void stopPreviousTask() {
        if (currentTask != null) {
            TaskManager.removeTask(currentTask.name);
            currentTask = null;
        }
    }

    private static class TickMeasuringTask extends SimpleTask {

        private static final int PERIOD = 100;

        private int tickCount = 0;
        private long totalTickTime = 0;
        private long startTickTime;
        private boolean hadFirstTick = false;
        private long firstTickStart;
        private long lastTickStart;

        private final boolean tps;
        private final boolean forceInaccurate;

        public TickMeasuringTask(boolean tps, boolean forceInaccurate) {
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
        protected void onTick() {
            if (tickCount >= PERIOD) {
                lastTickStart = System.nanoTime();
                _break();
            }
        }

        @Override
        public void initialize() {
            ClientCommandHelper.sendFeedback(Component.translatable("commands.ctick.measuring"));
        }

        @Override
        public void onCompleted() {
            if (tps) {
                long totalTime = lastTickStart - firstTickStart;
                double tps = 1_000_000_000D * tickCount / totalTime;
                ClientCommandHelper.sendFeedback(Component.translatable("commands.ctick.tps", totalTime == 0 ? Component.translatable("commands.ctick.tps.immeasurable") : DEC_FMT.format(tps)));
            } else if (forceInaccurate) {
                long totalTime = lastTickStart - firstTickStart;
                double mspt = totalTime / (1_000_000D * tickCount);
                ClientCommandHelper.sendFeedback(Component.translatable("commands.ctick.mspt", DEC_FMT.format(mspt)));
                ClientCommandHelper.sendHelp(Component.translatable("commands.ctick.mspt.inaccurate"));
            } else {
                double mspt = totalTickTime / (1_000_000D * tickCount);
                ClientCommandHelper.sendFeedback(Component.translatable("commands.ctick.mspt", DEC_FMT.format(mspt)));
            }
        }

        @Override
        public boolean condition() {
            return tickCount <= 1200;
        }
    }

    public static void registerEvents() {
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            if (currentTask != null && !currentTask.measurer.isCompleted() && !currentTask.measurer.forceInaccurate) {
                currentTask.measurer.startTick();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (currentTask != null && !currentTask.measurer.isCompleted() && !currentTask.measurer.forceInaccurate) {
                currentTask.measurer.endTick();
            }
        });

        MoreClientEvents.TIME_SYNC.register(new MoreClientEvents.TimeSync() {
            private long lastTick = -1;

            @Override
            public void onTimeSync(ClientboundSetTimePacket packet) {
                if (currentTask != null && !currentTask.measurer.isCompleted() && currentTask.measurer.forceInaccurate) {
                    long tick = packet.gameTime();
                    if (lastTick != -1) {
                        int deltaTick = (int) (tick - lastTick);
                        currentTask.measurer.incrTickCount(deltaTick);
                    }
                    lastTick = tick;
                }
            }
        });
    }

    private record CurrentTask(String name, TickMeasuringTask measurer) {
    }
}
