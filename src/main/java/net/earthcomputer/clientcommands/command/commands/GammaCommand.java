package net.earthcomputer.clientcommands.command.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class GammaCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cgamma");

        dispatcher.register(literal("cgamma")
            .then(argument("gamma", doubleArg())
                .executes(ctx -> setGamma(ctx.getSource(), getDouble(ctx, "gamma")))));
    }

    private static int setGamma(ServerCommandSource source, double gamma) {
        MinecraftClient.getInstance().options.gamma = gamma;

        Text feedback = new TranslatableText("commands.cgamma.success", gamma);
        sendFeedback(feedback);

        return 0;
    }

}
