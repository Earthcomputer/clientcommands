package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.Configs;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FramerateCommand {
    private static final int[] COMMON_REFRESH_RATES = new int[] {
        30, 45, 60, 75, 90, 100, 120, 144, 165, 180, 240, 300, 360, 420, 480, 540, 600
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfps")
            .executes(ctx -> getMaxFps(ctx.getSource()))
            .then(literal("unlimited")
                .executes(ctx -> maxFps(ctx.getSource(), Integer.MAX_VALUE)))
            .then(argument("maxfps", integer(1))
                .suggests((context, builder) -> {
                    int maxFps = getDisplayMaxFramerate();
                    for (int refreshRate : COMMON_REFRESH_RATES) {
                        if (refreshRate > maxFps) {
                            break;
                        }
                        builder.suggest(refreshRate);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> maxFps(ctx.getSource(), getInteger(ctx, "maxfps")))
            )
            .then(literal("reset")
                .executes(ctx -> resetMaxFps(ctx.getSource()))
            )
        );

    }

    private static int getMaxFps(FabricClientCommandSource source) {
        int framerateLimit = Configs.overriddenFps;
        if (framerateLimit <= 0) {
            framerateLimit = source.getClient().getFramerateLimitTracker().getFramerateLimit();
        }
        if (framerateLimit < Integer.MAX_VALUE) {
            source.sendFeedback(Component.translatable("commands.cfps.getMaxFps", framerateLimit));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.getMaxFps.unlimited"));
        }
        return framerateLimit;
    }

    private static int maxFps(FabricClientCommandSource source, int maxFps) {
        Configs.overriddenFps = maxFps;
        Configs.save();
        if (maxFps == Integer.MAX_VALUE) {
            source.sendFeedback(Component.translatable("commands.cfps.setMaxFps.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.setMaxFps", maxFps));
        }
        return maxFps;
    }

    private static int resetMaxFps(FabricClientCommandSource source) {
        Configs.overriddenFps = 0;
        Configs.save();
        source.sendFeedback(Component.translatable("commands.cfps.resetMaxFps.success"));
        return Command.SINGLE_SUCCESS;
    }

    private static int getDisplayMaxFramerate() {
        return Minecraft.getInstance().virtualScreen.screenManager.monitors.values().stream()
            .mapToInt(monitor -> monitor.getCurrentMode().getRefreshRate())
            .max().orElseThrow();
    }
}
