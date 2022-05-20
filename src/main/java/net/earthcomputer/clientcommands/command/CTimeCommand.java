package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.text.TranslatableText;

import static dev.xpple.clientarguments.arguments.CTimeArgumentType.getCTime;
import static dev.xpple.clientarguments.arguments.CTimeArgumentType.time;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

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
                    .executes(ctx -> executeSetTime(ctx.getSource(), getCTime(ctx, "time")))))
             .then(literal("reset")
                     .executes(ctx -> executeResetTime(ctx.getSource())))
        );
    }

    private static int executeQueryDay(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getTimeOfDay() / SharedConstants.TICKS_PER_IN_GAME_DAY % 2147483647L));
    }

    private static int executeQueryDayTime(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getTimeOfDay() % SharedConstants.TICKS_PER_IN_GAME_DAY));
    }

    private static int executeQueryGameTime(FabricClientCommandSource source) {
        return executeQuery(source, (int) (source.getWorld().getTime() % 2147483647L));
    }

    private static int executeQuery(FabricClientCommandSource source, int time) {
        source.sendFeedback(new TranslatableText("commands.time.query", time));
        return time;
    }
    
    private static int executeSetTime(FabricClientCommandSource source, int time) {
        ClientTimeModifier.lock(time);
        return executeQueryDayTime(source);
    }
    
    private static int executeResetTime(FabricClientCommandSource source) {
        ClientTimeModifier.none();
        return executeQueryDayTime(source);
    }
}
