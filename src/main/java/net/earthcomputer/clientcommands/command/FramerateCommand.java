package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FramerateCommand {

    public static final IntSupplier MAX_REFRESH_RATE = () -> Math.max(Options.UNLIMITED_FRAMERATE_CUTOFF, Minecraft.getInstance().virtualScreen.screenManager.monitors.values().stream()
        .mapToInt(monitor -> monitor.getCurrentMode().getRefreshRate())
        .max().orElseThrow());

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfps")
            .then(argument("maxfps", integer(1))
                .suggests((context, builder) -> builder.suggest(MAX_REFRESH_RATE.getAsInt()).buildFuture())
                .executes(ctx -> maxFps(ctx.getSource(), getInteger(ctx, "maxfps"))))
            .then(literal("unlimited")
                .executes(ctx -> maxFps(ctx.getSource(), MAX_REFRESH_RATE.getAsInt()))));
    }

    private static int maxFps(FabricClientCommandSource source, int maxFps) {
        int maxRefreshRate = MAX_REFRESH_RATE.getAsInt();
        boolean unlimited;
        if (maxFps >= maxRefreshRate) {
            maxFps = maxRefreshRate;
            unlimited = true;
        } else {
            unlimited = false;
        }
        source.getClient().getFramerateLimitTracker().setFramerateLimit(maxFps);
        if (unlimited) {
            source.sendFeedback(Component.translatable("commands.cfps.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.success", maxFps));
        }
        return maxFps;
    }
}
