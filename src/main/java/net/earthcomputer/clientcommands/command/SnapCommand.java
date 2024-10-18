package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import static dev.xpple.clientarguments.arguments.CVec3Argument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SnapCommand {
    public static boolean clickToTeleport = false;

    private static final SimpleCommandExceptionType TOO_FAR_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.csnap.tooFar"));
    private static final SimpleCommandExceptionType CANNOT_FIT_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.csnap.cannotFit"));

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

    private static int snap(FabricClientCommandSource source, Vec3 pos) throws CommandSyntaxException {
        if (source.getPosition().distanceToSqr(pos) > 1) {
            throw TOO_FAR_EXCEPTION.create();
        }
        if (!canStay(source.getPlayer(), pos)) {
            throw CANNOT_FIT_EXCEPTION.create();
        }
        source.getPlayer().setPos(pos);
        source.sendFeedback(Component.translatable("commands.csnap.success", pos.x, pos.y, pos.z));
        return Command.SINGLE_SUCCESS;
    }

    public static boolean canStay(LocalPlayer player, Vec3 pos) {
        return player.level().noBlockCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(pos).deflate(1e-7));
    }
}
