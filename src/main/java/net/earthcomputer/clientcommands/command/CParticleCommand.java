package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.ParticleArgumentType.*;
import static net.minecraft.command.argument.Vec3ArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CParticleCommand {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cparticle");

        LiteralCommandNode<ServerCommandSource> cparticle = dispatcher.register(literal("cparticle"));
        dispatcher.register(literal("cparticle")
            .then(argument("name", particle())
                .then(argument("pos", vec3())
                    .then(argument("delta", vec3(false))
                        .then(argument("speed", floatArg(0))
                            .then(argument("count", integer(0))
                                .then(literal("normal")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false)))
                                .then(literal("force")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), true)))
                                .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false)))
                            .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), 1, false)))
                        .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), 1, 1, false)))
                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), Vec3d.ZERO, 1, 1, false)))
                .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), client.player.getPos(), Vec3d.ZERO, 1, 1, false))));
    }

    private static int spawnParticle(ServerCommandSource source, ParticleEffect parameters, Vec3d pos, Vec3d delta, float speed, int count, boolean force) {

        for (int i = 0; i < count; i++) {
            client.world.addParticle(parameters, force, pos.x + delta.x, pos.y + delta.y, pos.z + delta.z, speed, speed, speed);
        }

        sendFeedback(new TranslatableText("commands.cparticle.success", Registry.PARTICLE_TYPE.getId(parameters.getType()).toString()));
        return 0;
    }
}
