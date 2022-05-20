package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class FovCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfov")
            .then(argument("fov", doubleArg())
                .executes(ctx -> setFov(ctx.getSource(), getDouble(ctx, "fov"))))
            .then(literal("normal")
                .executes(ctx -> setFov(ctx.getSource(), 70)))
            .then(literal("quakePro")
                .executes(ctx -> setFov(ctx.getSource(), 110))));
    }

    private static int setFov(FabricClientCommandSource source, double fov) {
        source.getClient().options.fov = fov;

        Text feedback = new TranslatableText("commands.cfov.success", fov);
        source.sendFeedback(feedback);

        return 0;
    }

}
