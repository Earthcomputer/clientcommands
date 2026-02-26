package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FovCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfov")
            .then(literal("vertical")
                .then(argument("fov", integer(0, 360))
                    .executes(ctx -> setFov(ctx.getSource(), getInteger(ctx, "fov")))))
            .then(literal("normal")
                .executes(ctx -> setFov(ctx.getSource(), 70)))
            .then(literal("quakePro")
                .executes(ctx -> setFov(ctx.getSource(), 110)))
            .then(literal("horizontal")
                .then(argument("fov", integer(0, 360))
                    .executes(ctx -> setHorizontalFov(ctx.getSource(), getInteger(ctx, "fov"))))));
    }

    private static int setFov(FabricClientCommandSource source, int fov) {
        source.getClient().options.fov().value = fov;

        Component feedback = Component.translatable("commands.cfov.success", fov);
        source.sendFeedback(feedback);

        return fov;
    }

    private static int setHorizontalFov(FabricClientCommandSource source, int fov) {
        Window window = source.getClient().getWindow();

        double fovRad = Math.toRadians(fov);
        double aspectRatio = (double) window.getGuiScaledWidth() / window.getGuiScaledHeight();
        double verticalFovRad = 2 * Math.atan(Math.tan(fovRad / 2) / aspectRatio);
        int verticalFov = (int) Math.round(Math.toDegrees(verticalFovRad));

        return setFov(source, verticalFov);
    }
}
