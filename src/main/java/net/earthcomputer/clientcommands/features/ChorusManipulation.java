package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.features.PlayerRandCracker.*;

public class ChorusManipulation {
    //If the goal is relative or not to the player
    public static boolean chorusRelativeTel;
    public static Vec3d chorusGoalFrom;
    public static Vec3d chorusGoalTo;
    private static final Object GOAL_POS_KEY = new Object();

    public static void onChorusManipEnabled() {
        TaskManager.addTask("chorusManipRenderer", new SimpleTask() {
            @Override
            public boolean condition() {
                return TempRules.getChorusManipulation() && MinecraftClient.getInstance().player != null;
            }

            @Override
            protected void onTick() {
                if (chorusGoalFrom != null && chorusGoalTo != null) {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    assert player != null;
                    RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, GOAL_POS_KEY, getTargetArea(player.getPos()), 0xff55ff, 1);
                }
            }
        });
    }

    public static int setGoal(Vec3d v1, Vec3d v2, boolean relative) {
        if (!TempRules.getChorusManipulation()) {
            Text text = new TranslatableText("chorusManip.needChorusManipulation")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/ctemprule set chorusManipulation true"));
            sendFeedback(text);
            return 0;
        }

        if (!TempRules.playerCrackState.knowsSeed()) {
            Text text = new TranslatableText("playerManip.uncracked")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
            sendFeedback(text);
            return 0;
        }

        if (relative &&
                (Math.abs(v1.getX()) > 8.0 || Math.abs(v1.getY()) > 8.0 || Math.abs(v1.getZ()) > 8.0) &&
                (Math.abs(v2.getX()) > 8.0 || Math.abs(v2.getY()) > 8.0 || Math.abs(v2.getZ()) > 8.0)) {
            sendError(new TranslatableText("chorusManip.goalTooFar"));
            return -1;
        }

        if (Math.abs(v1.getY() - v2.getY()) == 0.0) {
            v2 = v2.add(0, 1, 0);
        }

        chorusGoalFrom = v1;
        chorusGoalTo = v2;
        chorusRelativeTel = relative;

        sendFeedback(new TranslatableText("chorusManip.setGoal",
                (relative ? "relative" : "absolute"),
                chorusGoalFrom.toString(), chorusGoalTo.toString()));
        return 0;
    }

    public static boolean onEat(Vec3d pos, int particleCount, int itemUseTimeLeft) {
        Box area = getTargetArea(pos);
        if (!area.expand(8.0).contains(pos)) {
            sendError(new TranslatableText("chorusManip.goalTooFar"));
            return false;
        }

        var throwItemsState =
                throwItemsUntil(rand -> {

                    if (particleCount != 16 && itemUseTimeLeft >= 0) {
                        //159 - (7-(itemUseTimeLeft/4)) * 18 = 33 + 4.5 * itemUseTimeLeft
                        for (int i = 0; i < 33 + 4.5 * itemUseTimeLeft; i++) {
                            rand.nextInt();
                        }
                    }

                    final double x = (rand.nextDouble() - 0.5D) * 16.0D + pos.getX();
                    ClientWorld world = MinecraftClient.getInstance().world;
                    assert world != null;
                    final double y = MathHelper.clamp(pos.getY() + (double) (rand.nextInt(16) - 8), world.getBottomY(), (world.getBottomY() + world.getLogicalHeight() - 1));
                    final double z = (rand.nextDouble() - 0.5D) * 16.0D + pos.getZ();
                    final Vec3d landingArea = canTeleport(area, new Vec3d(x, y, z));

                    if (landingArea != null) {
                        if (itemUseTimeLeft == 24) { // || itemUseTimeLeft == 0
                            sendFeedback(new TranslatableText("chorusManip.landing.success", Math.round(landingArea.getX() * 100) / 100.0,
                                    Math.round(landingArea.getY() * 100) / 100.0,
                                    Math.round(landingArea.getZ() * 100) / 100.0));
                        }
                        return true;
                    } else {
                        return false;
                    }
                }, TempRules.maxChorusItemThrows);
        if (!throwItemsState.getType().isSuccess()) {
            sendError(throwItemsState.getMessage());
            MinecraftClient.getInstance().inGameHud.setOverlayMessage(
                    new TranslatableText("chorusManip.landing.failed").formatted(Formatting.RED), false);
            return false;
        } else {
            return true;
        }
    }

    private static Box getTargetArea(Vec3d pos) {
        Vec3d from;
        Vec3d to;
        if (chorusRelativeTel) {
            from = chorusGoalFrom.add(pos);
            to = chorusGoalTo.add(pos);
        } else {
            from = chorusGoalFrom;
            to = chorusGoalTo;
        }
        return new Box(from, to);
    }

    /**
     * Simulates the teleportation function from LivingEntity,
     * so the function checks whether a teleport would succeed
     *
     * @return The Position, where the player lands
     * @see net.minecraft.entity.LivingEntity#teleport(double, double, double, boolean) (Vec3d)
     */
    public static Vec3d canTeleport(Box goalArea, Vec3d goalVec) {
        BlockPos blockPos = new BlockPos(goalVec);
        World world = MinecraftClient.getInstance().world;

        if (world != null && world.isChunkLoaded(blockPos)) {
            boolean blockBelowIsGround = false;

            while (!blockBelowIsGround && blockPos.getY() > 0) {
                BlockPos blockPos2 = blockPos.down();
                BlockState blockState = world.getBlockState(blockPos2);
                if (blockState.getMaterial().blocksMovement()) {
                    blockBelowIsGround = true;
                } else {
                    blockPos = blockPos2;
                }
            }

            if (blockBelowIsGround) {
                goalVec = new Vec3d(goalVec.getX(), blockPos.getY(), goalVec.getZ());
                final Box boundingBox = new Box(goalVec.getX() - 0.3, goalVec.getY(), goalVec.getZ() - 0.3, goalVec.getX() + 0.3, goalVec.getY() + 1.8, goalVec.getZ() + 0.3);
                if (goalArea.contains(goalVec) && world.isSpaceEmpty(boundingBox) && !world.containsFluid(boundingBox)) {
                    return goalVec;
                }
            }
        }
        return null;
    }
}
