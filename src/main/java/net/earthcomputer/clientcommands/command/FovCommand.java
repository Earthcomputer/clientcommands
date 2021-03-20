package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class FovCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfov");

        dispatcher.register(literal("cfov")
            .then(argument("fov", doubleArg())
                .executes(ctx -> setGamma(ctx.getSource(), getDouble(ctx, "fov"))))
            .then(literal("normal")
                .executes(ctx -> setGamma(ctx.getSource(), 70)))
            .then(literal("quakePro")
                .executes(ctx -> setGamma(ctx.getSource(), 110))));
    }

    private static int setGamma(ServerCommandSource source, double fov) {
        MinecraftClient.getInstance().options.fov = fov;

        Text feedback = new TranslatableText("commands.cfov.success", fov);
        sendFeedback(feedback);

        return 0;
    }

}
