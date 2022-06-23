package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.earthcomputer.clientcommands.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FovCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfov")
            .then(argument("fov", integer(0, 360))
                .executes(ctx -> setFov(ctx.getSource(), getInteger(ctx, "fov"))))
            .then(literal("normal")
                .executes(ctx -> setFov(ctx.getSource(), 70)))
            .then(literal("quakePro")
                .executes(ctx -> setFov(ctx.getSource(), 110))));
    }

    private static int setFov(FabricClientCommandSource source, int fov) {
        ((SimpleOptionAccessor) (Object) source.getClient().options.getFov()).forceSetValue(fov);

        Text feedback = Text.translatable("commands.cfov.success", fov);
        source.sendFeedback(feedback);

        return Command.SINGLE_SUCCESS;
    }

}
