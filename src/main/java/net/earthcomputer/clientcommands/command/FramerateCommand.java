package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FramerateCommand {

    private static final int[] COMMON_REFRESH_RATES = new int[] {
        600,
        540,
        480,
        420,
        360,
        300,
        240,
        180,
        165,
        144,
        120,
        90,
        60,
        45,
        30,
        15,
        10
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfps")
            .executes(ctx -> getMaxFps(ctx.getSource()))
            .then(literal("unlimited")
                .executes(ctx -> maxFps(ctx.getSource(), Integer.MAX_VALUE))
            ).then(argument("maxfps", integer())
                .suggests((context, builder) -> {
                    int maxFps = getDisplayMaxFramerate();
                    for (int refreshRate : COMMON_REFRESH_RATES) {
                        if (refreshRate > maxFps) {
                            continue;
                        }
                        builder.suggest(refreshRate);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> maxFps(ctx.getSource(), getInteger(ctx, "maxfps")))
            )
        );

    }

    private static int getMaxFps(FabricClientCommandSource source) {
        int framerateLimit = source.getClient().getFramerateLimitTracker().getFramerateLimit();
        if (framerateLimit < Integer.MAX_VALUE) {
            source.sendFeedback(Component.translatable("commands.cfps.success.get", framerateLimit));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.success.get.unlimited"));
        }
        return framerateLimit;
    }

    private static int maxFps(FabricClientCommandSource source, int maxFps) {
        source.getClient().getFramerateLimitTracker().setFramerateLimit(maxFps);
        if (maxFps == Integer.MAX_VALUE) {
            source.sendFeedback(Component.translatable("commands.cfps.success.set.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.success.set", maxFps));
        }
        return maxFps;
    }

    private static int getDisplayMaxFramerate() {
        return Minecraft.getInstance().virtualScreen.screenManager.monitors.values().stream()
            .mapToInt(monitor -> monitor.getCurrentMode().getRefreshRate())
            .max().orElseThrow();
    }

}
