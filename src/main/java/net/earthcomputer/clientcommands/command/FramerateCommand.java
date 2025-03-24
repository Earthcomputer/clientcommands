package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FramerateCommand {

    private static final int[] COMMON_REFRESH_RATES = new int[] {
        10,
        15,
        30,
        45,
        60,
        90,
        120,
        144,
        165,
        180,
        240,
        300,
        360,
        420,
        480,
        540,
        600
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfps")
            .then(argument("maxfps", integer())
                .suggests((context, builder) -> {
                    for (int refreshRate : COMMON_REFRESH_RATES) {
                        builder.suggest(refreshRate);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> maxFps(ctx.getSource(), getInteger(ctx, "maxfps")))
            ).then(literal("unlimited")
                .executes(ctx -> maxFps(ctx.getSource(), Integer.MAX_VALUE))
            )
        );

    }

    private static int maxFps(FabricClientCommandSource source, int maxFps) {
        source.getClient().getFramerateLimitTracker().setFramerateLimit(maxFps);
        if (maxFps == Integer.MAX_VALUE) {
            source.sendFeedback(Component.translatable("commands.cfps.success.unlimited"));
        } else {
            source.sendFeedback(Component.translatable("commands.cfps.success", maxFps));
        }
        return maxFps;
    }

}
