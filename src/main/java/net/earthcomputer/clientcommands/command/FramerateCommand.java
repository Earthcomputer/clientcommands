package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FramerateCommand {

    public static final int MAX_FRAMERATE = Minecraft.getInstance().virtualScreen.screenManager.monitors.values().stream()
        .mapToInt(monitor -> monitor.getCurrentMode().getRefreshRate())
        .min().orElseThrow();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfps")
            .then(argument("maxfps", integer(1, MAX_FRAMERATE))
                .suggests((context, builder) -> builder.suggest(MAX_FRAMERATE).buildFuture())
                .executes(ctx -> maxFps(ctx.getSource(), getInteger(ctx, "maxfps")))));
    }

    private static int maxFps(FabricClientCommandSource source, int maxFps) {
        source.getClient().getFramerateLimitTracker().setFramerateLimit(maxFps);
        source.sendFeedback(Component.translatable("commands.cfps.success", maxFps));
        return maxFps;
    }
}
