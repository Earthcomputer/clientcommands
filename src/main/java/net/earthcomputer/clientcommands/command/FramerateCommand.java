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
            .executes(ctx -> getMaxFps(ctx.getSource()))
            .then(argument("maxfps", integer(1))
                .suggests((context, builder) -> builder.suggest(MAX_REFRESH_RATE.getAsInt()).buildFuture())
                .executes(ctx -> setMaxFps(ctx.getSource(), getInteger(ctx, "maxfps"))))
            .then(literal("unlimited")
                .executes(ctx -> setMaxFps(ctx.getSource(), MAX_REFRESH_RATE.getAsInt() + 1))));
    }

    private static int getMaxFps(FabricClientCommandSource source) {
        int framerateLimit = source.getClient().getFramerateLimitTracker().getFramerateLimit();
        if (framerateLimit > MAX_REFRESH_RATE.getAsInt()) {
            source.sendFeedback(Component.translatable("commands.cfps.getMaxFps.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.getMaxFps", framerateLimit));
        }
        return framerateLimit;
    }

    private static int setMaxFps(FabricClientCommandSource source, int maxFps) {
        int maxRefreshRate = MAX_REFRESH_RATE.getAsInt();
        boolean unlimited;
        if (maxFps > maxRefreshRate) {
            maxFps = maxRefreshRate;
            unlimited = true;
        } else {
            unlimited = false;
        }
        source.getClient().getFramerateLimitTracker().setFramerateLimit(maxFps);
        if (unlimited) {
            source.sendFeedback(Component.translatable("commands.cfps.setMaxFps.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.setMaxFps.success", maxFps));
        }
        return maxFps;
    }
}
