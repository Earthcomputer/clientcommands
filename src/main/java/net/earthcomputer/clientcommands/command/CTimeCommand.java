package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;

import static dev.xpple.clientarguments.arguments.CTimeArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTimeCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctime")
            .then(literal("query")
                .then(literal("day")
                    .executes(ctx -> executeQueryDay(ctx.getSource())))
                .then(literal("daytime")
                    .executes(ctx -> executeQueryDayTime(ctx.getSource())))
                .then(literal("gametime")
                    .executes(ctx -> executeQueryGameTime(ctx.getSource()))))
            .then(literal("set")
                .then(literal("day")
                    .executes(ctx -> executeSetTime(ctx.getSource(), 1000)))
                .then(literal("noon")
                    .executes(ctx -> executeSetTime(ctx.getSource(), 6000)))
                .then(literal("night")
                    .executes(ctx -> executeSetTime(ctx.getSource(), 13000)))
                .then(literal("midnight")
                    .executes(ctx -> executeSetTime(ctx.getSource(), 18000)))
                .then(argument("time", time())
                    .executes(ctx -> executeSetTime(ctx.getSource(), getTime(ctx, "time")))))
             .then(literal("reset")
                     .executes(ctx -> executeResetTime(ctx.getSource())))
        );
    }

    private static int executeQueryDay(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getDayTime() / SharedConstants.TICKS_PER_GAME_DAY % 2147483647L));
    }

    private static int executeQueryDayTime(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getDayTime() % SharedConstants.TICKS_PER_GAME_DAY));
    }

    private static int executeQueryGameTime(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getGameTime() % 2147483647L));
    }

    private static int executeQuery(FabricClientCommandSource source, int time) {
        source.sendFeedback(Component.translatable("commands.time.query", time));
        return time;
    }

    private static int executeSetTime(FabricClientCommandSource source, int time) {
        ClientTimeModifier.lock(time);
        return executeQueryDayTime(source);
    }

    private static int executeResetTime(FabricClientCommandSource source) {
        ClientTimeModifier.none();
        source.sendFeedback(Component.translatable("commands.ctime.reset.success"));
        return Command.SINGLE_SUCCESS;
    }
}
