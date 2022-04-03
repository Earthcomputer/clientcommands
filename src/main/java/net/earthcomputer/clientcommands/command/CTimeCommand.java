package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

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
                    .executes(ctx -> executeQueryGameTime()))));
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

}
