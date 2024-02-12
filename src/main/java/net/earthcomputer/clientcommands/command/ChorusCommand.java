package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.xpple.clientarguments.arguments.CPosArgument;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.Direction;

import java.util.EnumSet;

import static dev.xpple.clientarguments.arguments.CVec3ArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ChorusCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
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

    public static RequiredArgumentBuilder<FabricClientCommandSource, CPosArgument> areaThen(boolean relative) {
        return argument("posFrom", vec3())
                .then(argument("posTo", vec3())
                        .executes(ctx -> ChorusManipulation.setGoal(getCVec3(ctx, "posFrom"), getCVec3(ctx, "posTo"), relative)));
    }

    public static RequiredArgumentBuilder<FabricClientCommandSource, CPosArgument> blockThen(boolean relative) {
        return argument("posGoal", vec3())
                .executes(ctx -> ChorusManipulation.setGoal(
                        getCVec3(ctx, "posGoal").align(EnumSet.allOf(Direction.Axis.class)).add(-0.2, 0, -0.2),
                        getCVec3(ctx, "posGoal").align(EnumSet.allOf(Direction.Axis.class)).add(1.2, 1, 1.2), relative))
                .then(literal("--perfectly")
                        .executes(ctx -> ChorusManipulation.setGoal(
                                getCVec3(ctx, "posGoal").align(EnumSet.allOf(Direction.Axis.class)).add(0.3, 0, 0.3),
                                getCVec3(ctx, "posGoal").align(EnumSet.allOf(Direction.Axis.class)).add(0.7, 1, 0.7), relative)));
    }
}
