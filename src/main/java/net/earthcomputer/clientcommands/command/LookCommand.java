package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.arguments.PosArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.arguments.BlockPosArgumentType.*;
import static net.minecraft.command.arguments.RotationArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class LookCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("clook");

        dispatcher.register(literal("clook")
            .then(literal("block")
                .then(argument("pos", blockPos())
                    .executes(ctx -> lookBlock(getBlockPos(ctx, "pos")))))
            .then(literal("angles")
                .then(argument("rotation", rotation())
                    .executes(ctx -> lookAngles(ctx.getSource(), getRotation(ctx, "rotation")))))
            .then(literal("cardinal")
                .then(literal("down")
                    .executes(ctx -> lookCardinal(ctx.getSource().getRotation().y, 90)))
                .then(literal("up")
                    .executes(ctx -> lookCardinal(ctx.getSource().getRotation().y, -90)))
                .then(literal("west")
                    .executes(ctx -> lookCardinal(90, 0)))
                .then(literal("east")
                    .executes(ctx -> lookCardinal(-90, 0)))
                .then(literal("north")
                    .executes(ctx -> lookCardinal(-180, 0)))
                .then(literal("south")
                    .executes(ctx -> lookCardinal(0, 0)))));
    }

    private static int lookBlock(BlockPos pos) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        double dx = (pos.getX() + 0.5) - player.getX();
        double dy = (pos.getY() + 0.5) - (player.getY() + player.getStandingEyeHeight());
        double dz = (pos.getZ() + 0.5) - player.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
        return doLook(player, yaw, pitch);
    }

    private static int lookAngles(ServerCommandSource source, PosArgument rotation) {
        Vec2f rot = rotation.toAbsoluteRotation(source);
        return doLook(MinecraftClient.getInstance().player, rot.y, rot.x);
    }

    private static int lookCardinal(float yaw, float pitch) {
        return doLook(MinecraftClient.getInstance().player, yaw, pitch);
    }

    private static int doLook(ClientPlayerEntity player, float yaw, float pitch) {
        player.setPositionAndAngles(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        return 0;
    }

}
