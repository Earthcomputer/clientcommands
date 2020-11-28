package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.features.PlayerRandCracker.throwItemsUntil;
import static net.minecraft.command.argument.Vec3ArgumentType.getVec3;
import static net.minecraft.command.argument.Vec3ArgumentType.vec3;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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

    public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, net.minecraft.command.argument.PosArgument> areaThen(boolean relative) {
        return argument("pos1", vec3())
                .then(argument("pos2", vec3())
                    .executes(ctx -> setGoal(getVec3(ctx, "pos1"), getVec3(ctx, "pos2"), relative)));
    }

    public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, net.minecraft.command.argument.PosArgument> blockThen(boolean relative) {
        return argument("pos", vec3())
                .executes(ctx -> setGoal(
                    getVec3(ctx, "pos").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)),
                    getVec3(ctx, "pos").add(1, 2, 1).floorAlongAxes(EnumSet.allOf(Direction.Axis.class)), relative))
                .then(literal("--perfectly")
                    .executes(ctx -> setGoal(
                        getVec3(ctx, "pos").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(0.3, 0, 0.3),
                        getVec3(ctx, "pos").floorAlongAxes(EnumSet.allOf(Direction.Axis.class)).add(0.7, 2, 0.7), relative)));
    }

    public static int setGoal(Vec3d v1, Vec3d v2, boolean relative) {
        if (relative &&
            (Math.abs(v1.getX()) > 8.0 || Math.abs(v1.getY()) > 8.0 || Math.abs(v1.getZ()) > 8.0) &&
            (Math.abs(v2.getX()) > 8.0 || Math.abs(v2.getY()) > 8.0 || Math.abs(v2.getZ()) > 8.0)) {
            sendError(new LiteralText("Goal is to far away!"));
            return -1;
        }
        TempRules.chorusGoalV1 = v1;
        TempRules.chorusGoalV2 = v2;
        TempRules.chorusRelativeTel = relative;
        sendFeedback("Set " +
                (relative ? "relative" : "absolute")
                + " goal area from " + TempRules.chorusGoalV1 + " to " + TempRules.chorusGoalV2);
        return 0;
    }

    public static boolean onEat(Vec3d pos, int particleCount, int itemUseTimeLeft) {
        Vec3d tempGoalV1;
        Vec3d tempGoalV2;

        if (TempRules.chorusRelativeTel) {
            tempGoalV1 = TempRules.chorusGoalV1.add(pos);
            tempGoalV2 = TempRules.chorusGoalV2.add(pos);
        } else {
            //Check if goal is even in range
            tempGoalV1 = TempRules.chorusGoalV1.subtract(pos);
            tempGoalV2 = TempRules.chorusGoalV2.subtract(pos);
            if (!(Math.abs(tempGoalV1.getX()) > 8.0 || Math.abs(tempGoalV1.getY()) > 8.0 || Math.abs(tempGoalV1.getZ()) > 8.0) ||
                !(Math.abs(tempGoalV2.getX()) > 8.0 || Math.abs(tempGoalV2.getY()) > 8.0 || Math.abs(tempGoalV2.getZ()) > 8.0)) {
                tempGoalV1 = TempRules.chorusGoalV1;
                tempGoalV2 = TempRules.chorusGoalV2;
            } else {
                sendError(new LiteralText("Goal is to far away!"));
                return false;
            }
        }

        final Vec3d GoalV1 = new Vec3d(Math.min(tempGoalV1.getX(), tempGoalV2.getX()), Math.min(tempGoalV1.getY(), tempGoalV2.getY()), Math.min(tempGoalV1.getZ(), tempGoalV2.getZ()));
        final Vec3d GoalV2 = new Vec3d(Math.max(tempGoalV1.getX(), tempGoalV2.getX()), Math.max(tempGoalV1.getY(), tempGoalV2.getY()), Math.max(tempGoalV1.getZ(), tempGoalV2.getZ()));

        return throwItemsUntil(rand -> {
            if (particleCount != 16)
                //159 - (7-(itemUseTimeLeft/4)) * 18 = 33 + 4.5 * itemUseTimeLeft
                for (int i = 0; i < 33 + 4.5 * itemUseTimeLeft; i++)
                    rand.nextInt();


            double x = (rand.nextDouble() - 0.5D) * 16.0D + pos.getX();
            double y = rand.nextInt(16) - 8 + pos.getY();
            double z = (rand.nextDouble() - 0.5D) * 16.0D + pos.getZ();

            if (GoalV1.getX() < x && x < GoalV2.getX() &&
                    GoalV1.getY() < y && //The top coordinate is special... TODO: Check for things above
                    GoalV1.getZ() < z && z < GoalV2.getZ()) {
                if (itemUseTimeLeft == 24) // || itemUseTimeLeft == 24
                    sendFeedback("Landing on: " +
                            Math.round(x * 100) / 100.0 + ", " +
                            Math.round(y * 100) / 100.0 + ", " +
                            Math.round(z * 100) / 100.0);
                return true;
            } else {
                return false;
            }
        }, TempRules.maxEnchantItemThrows);
    }
}
