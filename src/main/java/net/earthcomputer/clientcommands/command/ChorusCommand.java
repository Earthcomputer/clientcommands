package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.Vec3ArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class ChorusCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cchorus");

        dispatcher.register(literal("cchorus")
                .then(literal("setGoal")
                        .then(literal("relative")
                                .then(literal("area")
                                        .then(areaThen(true)))
                                .then(literal("block")
                                        .then(blockThen(true))))
                        .then(literal("absolute")
                                .then(literal("area")
                                        .then(areaThen(false)))
                                .then(literal("block")
                                        .then(blockThen(false))))
                ));
    }

    public static RequiredArgumentBuilder<ServerCommandSource, PosArgument> areaThen(boolean relative) {
        return argument("posFrom", vec3())
                .then(argument("posTo", vec3())
                        .executes(ctx -> ChorusManipulation.setGoal(getVec3(ctx, "posFrom"), getVec3(ctx, "posTo"), relative)));
    }

    public static RequiredArgumentBuilder<ServerCommandSource, PosArgument> blockThen(boolean relative) {
        return argument("posGoal", vec3())
                .executes(ctx -> ChorusManipulation.setGoal(
                        getVec3(ctx, "posGoal").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(-0.2, 0, -0.2),
                        getVec3(ctx, "posGoal").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(1.2, 1, 1.2), relative))
                .then(literal("--perfectly")
                        .executes(ctx -> ChorusManipulation.setGoal(
                                getVec3(ctx, "posGoal").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(0.3, 0, 0.3),
                                getVec3(ctx, "posGoal").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(0.7, 1, 0.7), relative)));
    }
}
