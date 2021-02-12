package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.earthcomputer.clientcommands.features.SpeedManipulation;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SpeedCommand {
    public static double SPEED;
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cspeed");

        dispatcher.register(literal("cspeed")
                .then(argument("speed" , doubleArg())
                    .executes(ctx -> setSpeed(ctx.getSource(), getDouble(ctx, "speed")))));
    }



    private static int setSpeed(ServerCommandSource source, double speed) throws CommandSyntaxException {
        SPEED = speed;
        SpeedManipulation.setSpeed(speed);
        Text feedback = new TranslatableText("commands.cspeed.success", speed);
        sendFeedback(feedback);

        return 0;
    }

}
