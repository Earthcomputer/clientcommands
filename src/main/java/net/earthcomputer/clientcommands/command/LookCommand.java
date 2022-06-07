package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.xpple.clientarguments.arguments.CPosArgument;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CRotationArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class LookCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("clook")
            .then(literal("block")
                .then(argument("pos", blockPos())
                    .executes(ctx -> lookBlock(ctx.getSource(), getCBlockPos(ctx, "pos")))))
            .then(literal("angles")
                .then(argument("rotation", rotation())
                    .executes(ctx -> lookAngles(ctx.getSource(), getCRotation(ctx, "rotation")))))
            .then(literal("cardinal")
                .then(literal("down")
                    .executes(ctx -> lookCardinal(ctx.getSource(), 90)))
                .then(literal("up")
                    .executes(ctx -> lookCardinal(ctx.getSource(), -90)))
                .then(literal("west")
                    .executes(ctx -> lookCardinal(ctx.getSource(), 90, 0)))
                .then(literal("east")
                    .executes(ctx -> lookCardinal(ctx.getSource(),-90, 0)))
                .then(literal("north")
                    .executes(ctx -> lookCardinal(ctx.getSource(), -180, 0)))
                .then(literal("south")
                    .executes(ctx -> lookCardinal(ctx.getSource(), 0, 0)))));
    }

    private static int lookBlock(FabricClientCommandSource source, BlockPos pos) {
        ClientPlayerEntity player = source.getPlayer();
        double dx = (pos.getX() + 0.5) - player.getX();
        double dy = (pos.getY() + 0.5) - (player.getY() + player.getStandingEyeHeight());
        double dz = (pos.getZ() + 0.5) - player.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
        return doLook(player, yaw, pitch);
    }

    private static int lookAngles(FabricClientCommandSource source, CPosArgument rotation) {
        Vec2f rot = rotation.toAbsoluteRotation(source);
        return doLook(source.getPlayer(), rot.y, rot.x);
    }

    private static int lookCardinal(FabricClientCommandSource source, float yaw, float pitch) {
        return doLook(source.getPlayer(), yaw, pitch);
    }

    private static int lookCardinal(FabricClientCommandSource source, float pitch) {
        return lookCardinal(source, source.getRotation().y, pitch);
    }

    private static int doLook(ClientPlayerEntity player, float yaw, float pitch) {
        player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        return 0;
    }

}
