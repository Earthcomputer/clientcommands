package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.earthcomputer.clientcommands.features.ClientTimeModifier;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;
import static net.minecraft.command.argument.TimeArgumentType.time;

public class CTimeCommand {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctime")
            .then(literal("query")
                .then(literal("day")
                    .executes(ctx -> executeQueryDay()))
                .then(literal("daytime")
                    .executes(ctx -> executeQueryDayTime()))
                .then(literal("gametime")
                    .executes(ctx -> executeQueryGameTime())))
            .then(literal("set")
                .then(literal("day")
                    .executes(ctx -> executeSetTime(1000)))
                .then(literal("noon")
                    .executes(ctx -> executeSetTime(6000)))
                .then(literal("night")
                    .executes(ctx -> executeSetTime(13000)))
                .then(literal("midnight")
                    .executes(ctx -> executeSetTime(18000)))
                .then(argument("time", time())
                    .executes(ctx -> executeSetTime(getInteger(ctx, "time")))))
             .then(literal("reset")
                     .executes(ctx -> executeResetTime())) 
        );
    }

    private static int executeQueryDay() {
        return executeQuery((int) (CLIENT.world.getTimeOfDay() / SharedConstants.TICKS_PER_IN_GAME_DAY % 2147483647L));
    }

    private static int executeQueryDayTime() {
        return executeQuery((int) (CLIENT.world.getTimeOfDay() % SharedConstants.TICKS_PER_IN_GAME_DAY));
    }

    private static int executeQueryGameTime() {
        return executeQuery((int) (CLIENT.world.getTime() % 2147483647L));
    }

    private static int executeQuery(int time) {
        sendFeedback(new TranslatableText("commands.time.query", time));
        return time;
    }
    
    private static int executeSetTime(int time) {
        ClientTimeModifier.lock(time);
        return executeQueryDayTime();
    }
    
    private static int executeResetTime() {
        ClientTimeModifier.none();
        return executeQueryDayTime();
    }
}
