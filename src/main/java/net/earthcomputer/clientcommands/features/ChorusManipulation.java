package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.features.PlayerRandCracker.*;

public class ChorusManipulation {
    //If the goal is relative or not to the player
    public static boolean chorusRelativeTel;
    @Nullable
    public static Vec3 chorusGoalFrom;
    @Nullable
    public static Vec3 chorusGoalTo;
    private static final Object GOAL_POS_KEY = new Object();

    public static void onChorusManipEnabled() {
        TaskManager.addTask("chorusManipRenderer", new SimpleTask() {
            @Override
            public boolean condition() {
                return Configs.getChorusManipulation() && Minecraft.getInstance().player != null;
            }

            @Override
            protected void onTick() {
                if (chorusGoalFrom != null && chorusGoalTo != null) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    assert player != null;
                    RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, GOAL_POS_KEY, getTargetArea(player.position()), 0xff55ff, 1);
                }
            }
        });
    }

    public static int setGoal(Vec3 v1, Vec3 v2, boolean relative) {
        if (!Configs.getChorusManipulation()) {
            Component component = Component.translatable("chorusManip.needChorusManipulation")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/cconfig clientcommands chorusManipulation set true"));
            sendFeedback(component);
            return 0;
        }

        if (!Configs.playerCrackState.knowsSeed()) {
            Component component = Component.translatable("playerManip.uncracked")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
            sendFeedback(component);
            return 0;
        }

        if (relative &&
                (Math.abs(v1.x()) > 8.0 || Math.abs(v1.y()) > 8.0 || Math.abs(v1.z()) > 8.0) &&
                (Math.abs(v2.x()) > 8.0 || Math.abs(v2.y()) > 8.0 || Math.abs(v2.z()) > 8.0)) {
            sendError(Component.translatable("chorusManip.goalTooFar"));
            return -1;
        }

        if (Math.abs(v1.y() - v2.y()) == 0.0) {
            v2 = v2.add(0, 1, 0);
        }

        chorusGoalFrom = v1;
        chorusGoalTo = v2;
        chorusRelativeTel = relative;

        sendFeedback(Component.translatable("chorusManip.setGoal",
                (relative ? "relative" : "absolute"),
                chorusGoalFrom.toString(), chorusGoalTo.toString()));
        return 0;
    }

    public static boolean onEat(Vec3 pos, int particleCount, int itemUseTimeLeft) {
        AABB area = getTargetArea(pos);
        if (area == null) {
            return false;
        }

        if (!area.inflate(8.0).contains(pos)) {
            sendError(Component.translatable("chorusManip.goalTooFar"));
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

                    final double x = (rand.nextDouble() - 0.5D) * 16.0D + pos.x();
                    ClientLevel level = Minecraft.getInstance().level;
                    assert level != null;
                    final double y = Mth.clamp(pos.y() + (double) (rand.nextInt(16) - 8), level.getMinBuildHeight(), (level.getMinBuildHeight() + level.getHeight() - 1));
                    final double z = (rand.nextDouble() - 0.5D) * 16.0D + pos.z();
                    final Vec3 landingArea = canTeleport(area, new Vec3(x, y, z));

                    if (landingArea != null) {
                        if (itemUseTimeLeft == 24) { // || itemUseTimeLeft == 0
                            sendFeedback(Component.translatable("chorusManip.landing.success", Math.round(landingArea.x() * 100) / 100.0,
                                    Math.round(landingArea.y() * 100) / 100.0,
                                    Math.round(landingArea.z() * 100) / 100.0));
                        }
                        return true;
                    } else {
                        return false;
                    }
                }, Configs.getMaxChorusItemThrows());
        if (!throwItemsState.getType().isSuccess()) {
            sendError(throwItemsState.getMessage());
            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.translatable("chorusManip.landing.failed").withStyle(ChatFormatting.RED), false);
            return false;
        } else {
            return true;
        }
    }

    @Nullable
    private static AABB getTargetArea(Vec3 pos) {
        if (chorusGoalFrom == null || chorusGoalTo == null) {
            return null;
        }

        Vec3 from;
        Vec3 to;
        if (chorusRelativeTel) {
            from = chorusGoalFrom.add(pos);
            to = chorusGoalTo.add(pos);
        } else {
            from = chorusGoalFrom;
            to = chorusGoalTo;
        }
        return new AABB(from, to);
    }

    /**
     * Simulates the teleportation function from LivingEntity,
     * so the function checks whether a teleport would succeed
     *
     * @return The Position, where the player lands
     * @see net.minecraft.world.entity.LivingEntity#randomTeleport(double, double, double, boolean)  (Vec3d)
     */
    public static Vec3 canTeleport(AABB goalArea, Vec3 goalVec) {
        BlockPos blockPos = BlockPos.containing(goalVec);
        Level level = Minecraft.getInstance().level;

        if (level != null && level.hasChunkAt(blockPos)) {
            boolean blockBelowIsGround = false;

            while (!blockBelowIsGround && blockPos.getY() > 0) {
                BlockPos blockPos2 = blockPos.below();
                BlockState blockState = level.getBlockState(blockPos2);
                if (blockState.blocksMotion()) {
                    blockBelowIsGround = true;
                } else {
                    blockPos = blockPos2;
                }
            }

            if (blockBelowIsGround) {
                goalVec = new Vec3(goalVec.x(), blockPos.getY(), goalVec.z());
                final AABB boundingBox = new AABB(goalVec.x() - 0.3, goalVec.y(), goalVec.z() - 0.3, goalVec.x() + 0.3, goalVec.y() + 1.8, goalVec.z() + 0.3);
                if (goalArea.contains(goalVec) && level.noCollision(boundingBox) && !level.containsAnyLiquid(boundingBox)) {
                    return goalVec;
                }
            }
        }
        return null;
    }
}
