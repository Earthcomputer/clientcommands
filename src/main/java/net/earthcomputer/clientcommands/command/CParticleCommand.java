package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.Random;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.ParticleEffectArgumentType.*;
import static net.minecraft.command.argument.Vec3ArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CParticleCommand {

    private static final SimpleCommandExceptionType UNSUITABLE_PARTICLE_OPTION_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cparticle.unsuitableParticleOption"));

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cparticle");

        dispatcher.register(literal("cparticle")
            .then(argument("name", particleEffect())
                .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), client.player.getPos(), Vec3d.ZERO, 1, 1, false))
                .then(argument("pos", vec3())
                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), Vec3d.ZERO, 1, 1, false))
                    .then(argument("delta", vec3(false))
                        .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), 1, 1, false))
                        .then(argument("speed", floatArg(0))
                            .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), 1, false))
                            .then(argument("count", integer(0))
                                .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false))
                                .then(literal("normal")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false)))
                                .then(literal("force")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getParticle(ctx, "name"), getVec3(ctx, "pos"), getVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), true)))))))));
    }

    private static int spawnParticle(ServerCommandSource source, ParticleEffect parameters, Vec3d pos, Vec3d delta, float speed, int count, boolean force) throws CommandSyntaxException {
        switch (client.options.particles) {
            case MINIMAL:
                if (!force) {
                    throw UNSUITABLE_PARTICLE_OPTION_EXCEPTION.create();
                }
            case DECREASED:
                if ((parameters.getType() == ParticleTypes.RAIN || parameters.getType() == ParticleTypes.SMOKE) && !force) {
                    throw UNSUITABLE_PARTICLE_OPTION_EXCEPTION.create();
                }
            default:
                if (count == 0) {
                    client.world.addParticle(parameters, force, pos.x, pos.y, pos.z, delta.x * speed, delta.y * speed, delta.z * speed);
                } else {
                    final Random random = client.getNetworkHandler().getWorld().random;
                    for (int i = 0; i < count; i++) {
                        client.world.addParticle(parameters, force, pos.x + delta.x * random.nextGaussian(), pos.y + delta.y * random.nextGaussian(), pos.z + delta.z * random.nextGaussian(), speed * random.nextGaussian(), speed * random.nextGaussian(), speed * random.nextGaussian());
                    }
                }
                sendFeedback(new TranslatableText("commands.cparticle.success", Registry.PARTICLE_TYPE.getId(parameters.getType()).toString()));
                return 0;
        }
    }
}
