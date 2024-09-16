package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import static dev.xpple.clientarguments.arguments.CVec3Argument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SnapCommand {

    public static boolean clickToTeleport = false;

    private static final SimpleCommandExceptionType TOO_FAR_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.csnap.tooFar"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csnap")
            .executes(ctx -> snap(ctx.getSource()))
            .then(argument("pos", vec3())
                .executes(ctx -> snap(ctx.getSource(), getVec3(ctx, "pos")))));
    }

    private static int snap(FabricClientCommandSource source) {
        clickToTeleport = !clickToTeleport;
        if (clickToTeleport) {
            source.sendFeedback(Component.translatable("commands.csnap.clickToTeleportEnabled"));
        } else {
            source.sendFeedback(Component.translatable("commands.csnap.clickToTeleportDisabled"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int snap(FabricClientCommandSource source, Vec3 vec3) throws CommandSyntaxException {
        if (source.getPosition().distanceToSqr(vec3) > 1) {
            throw TOO_FAR_EXCEPTION.create();
        }
        source.getClient().player.setPos(vec3);
        source.sendFeedback(Component.translatable("commands.csnap.success", vec3.x, vec3.y, vec3.z));
        return Command.SINGLE_SUCCESS;
    }
}
