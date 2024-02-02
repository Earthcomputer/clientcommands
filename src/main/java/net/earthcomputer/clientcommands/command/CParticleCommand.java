package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CParticleEffectArgumentType.*;
import static dev.xpple.clientarguments.arguments.CVec3ArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CParticleCommand {

    private static final SimpleCommandExceptionType UNSUITABLE_PARTICLE_OPTION_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cparticle.unsuitableParticleOption"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cparticle")
            .then(argument("name", particleEffect())
                .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), ctx.getSource().getPlayer().position(), Vec3.ZERO, 1, 1, false))
                .then(argument("pos", vec3())
                    .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), Vec3.ZERO, 1, 1, false))
                    .then(argument("delta", vec3(false))
                        .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), getCVec3(ctx, "delta"), 1, 1, false))
                        .then(argument("speed", floatArg(0))
                            .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), getCVec3(ctx, "delta"), getFloat(ctx, "speed"), 1, false))
                            .then(argument("count", integer(0))
                                .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), getCVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false))
                                .then(literal("normal")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), getCVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), false)))
                                .then(literal("force")
                                    .executes(ctx -> spawnParticle(ctx.getSource(), getCParticle(ctx, "name"), getCVec3(ctx, "pos"), getCVec3(ctx, "delta"), getFloat(ctx, "speed"), getInteger(ctx, "count"), true)))))))));
    }

    private static int spawnParticle(FabricClientCommandSource source, ParticleOptions parameters, Vec3 pos, Vec3 delta, float speed, int count, boolean force) throws CommandSyntaxException {
        switch (source.getClient().options.particles().get()) {
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
                    source.getWorld().addParticle(parameters, force, pos.x, pos.y, pos.z, delta.x * speed, delta.y * speed, delta.z * speed);
                } else {
                    final RandomSource random = source.getClient().getConnection().getLevel().random;
                    for (int i = 0; i < count; i++) {
                        source.getWorld().addParticle(parameters, force, pos.x + delta.x * random.nextGaussian(), pos.y + delta.y * random.nextGaussian(), pos.z + delta.z * random.nextGaussian(), speed * random.nextGaussian(), speed * random.nextGaussian(), speed * random.nextGaussian());
                    }
                }
                source.sendFeedback(Component.translatable("commands.cparticle.success",
                        BuiltInRegistries.PARTICLE_TYPE.getKey(parameters.getType()).toString()));
                return Command.SINGLE_SUCCESS;
        }
    }
}
