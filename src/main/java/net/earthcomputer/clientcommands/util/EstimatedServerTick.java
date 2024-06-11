package net.earthcomputer.clientcommands.util;

import net.earthcomputer.clientcommands.event.MoreClientEvents;
import net.earthcomputer.clientcommands.features.VillagerCracker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EstimatedServerTick {
    private static long lastServerSetTime = 0;
    private static long lastGameTime = 0;
    private static final AtomicLong estimatedNsPerTick = new AtomicLong(50_000_000L);
    private static final AtomicInteger ticksRemaining = new AtomicInteger(0);

    public static void onSetTime(long gameTime) {
        long ns = System.nanoTime();
        long estimatedNsPerTick;
        if (lastServerSetTime == 0) {
            // assuming 20 tps
            estimatedNsPerTick = 50_000_000L;
        } else {
            estimatedNsPerTick = (ns - lastServerSetTime) / Math.max(1, gameTime - lastGameTime);
        }
        lastGameTime = gameTime;
        lastServerSetTime = ns;
        EstimatedServerTick.estimatedNsPerTick.set(0);
        // spin loop
        while (ticksRemaining.get() > 0);
        EstimatedServerTick.estimatedNsPerTick.set(estimatedNsPerTick);
        ticksRemaining.set(20);
    }

    static {
        new Thread(() -> {
            long lastTickTimestamp = 0;

            while (true) {
                long ns = System.nanoTime();
                if (ns >= lastTickTimestamp + estimatedNsPerTick.get() && EstimatedServerTick.ticksRemaining.getAndDecrement() > 0) {
                    lastTickTimestamp = ns;
                    MoreClientEvents.ESTIMATED_SERVER_TICK.invoker().onEstimatedServerTick();
                }
            }
        }, "Estimated Server Tick Thread").start();

        MoreClientEvents.ESTIMATED_SERVER_TICK.register(VillagerCracker::onServerTick);
    }
}
