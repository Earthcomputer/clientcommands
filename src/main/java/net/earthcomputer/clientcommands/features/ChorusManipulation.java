package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

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
        TaskManager.addNonConflictingTask("chorusManipRenderer", new SimpleTask() {
            @Override
            public boolean condition() {
                return Configs.chorusManipulation && Minecraft.getInstance().player != null;
            }

            @Override
            protected void onTick() {
                if (chorusGoalFrom != null && chorusGoalTo != null) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    assert player != null;
                    AABB targetArea = getTargetArea(player.position());
                    if (targetArea != null) {
                        RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, GOAL_POS_KEY, targetArea, 0xff55ff, 1);
                    }
                }
            }
        });
    }

    public static int setGoal(Vec3 v1, Vec3 v2, boolean relative) {
        if (!Configs.chorusManipulation) {
            Component component = Component.translatable("chorusManip.needChorusManipulation")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(CComponentUtil.getCommandTextComponent("commands.client.enable", "/cconfig clientcommands chorusManipulation set true"));
            sendFeedback(component);
            return 0;
        }

        if (!Configs.playerCrackState.knowsSeed()) {
            Component component = Component.translatable("playerManip.uncracked")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(CComponentUtil.getCommandTextComponent("commands.client.crack", "/ccrackrng"));
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

    public static void onEat(Vec3 pos, int itemUseTimeLeft, ItemStack stack, Consumable consumable) {
        TeleportRandomlyConsumeEffect teleport = null;
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof TeleportRandomlyConsumeEffect teleportEffect) {
                teleport = teleportEffect;
                break;
            }
        }
        if (teleport == null) {
            return;
        }
        float diameter = teleport.diameter();

        AABB area = getTargetArea(pos);
        if (area == null) {
            return;
        }

        if (!area.inflate(diameter / 2).contains(pos)) {
            sendError(Component.translatable("chorusManip.goalTooFar"));
            return;
        }

        int randomCallsBeforeTeleport = getRandomCallsBeforeTeleport(itemUseTimeLeft, stack, consumable);
        @UnknownNullability Vec3[] landingPos = new Vec3[1];
        var throwItemsState = throwItemsUntil(rand -> {
            for (int i = 0; i < randomCallsBeforeTeleport; i++) {
                rand.nextInt();
            }
            landingPos[0] = simulateTeleport(rand, pos, diameter);
            return landingPos[0] != null && area.contains(landingPos[0]);
        }, Configs.getMaxChorusItemThrows());

        if (!throwItemsState.isSuccess()) {
            throwItemsState.sendErrorMessage();
            Minecraft.getInstance().gui.hud.setOverlayMessage(
                    Component.translatable("chorusManip.landing.failed").withStyle(ChatFormatting.RED), false);
        } else if (!consumable.shouldEmitParticlesAndSounds(itemUseTimeLeft + 4)) {
            // only send the success message on the first eating tick
            sendFeedback(Component.translatable("chorusManip.landing.success",
                    Math.round(landingPos[0].x() * 100) / 100.0,
                    Math.round(landingPos[0].y() * 100) / 100.0,
                    Math.round(landingPos[0].z() * 100) / 100.0));
        }
    }

    // The random calls from the remaining eating ticks, including this one, and from finishing eating
    private static int getRandomCallsBeforeTeleport(int itemUseTimeLeft, ItemStack stack, Consumable consumable) {
        int randomCalls = PlayerRandCracker.getConsumeRandomCalls(stack, 16, consumable);
        for (int ticksLeft = itemUseTimeLeft; ticksLeft > 0; ticksLeft--) {
            if (consumable.shouldEmitParticlesAndSounds(ticksLeft)) {
                randomCalls += PlayerRandCracker.getConsumeRandomCalls(stack, 5, consumable);
            }
        }
        return randomCalls;
    }

    /**
     * Simulates the random calls and teleport attempts of the chorus fruit teleport
     *
     * @return The position the player teleports to, or {@code null} if all attempts fail
     * @see net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect#apply
     */
    @Nullable
    public static Vec3 simulateTeleport(RandomSource rand, Vec3 pos, float diameter) {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            return null;
        }

        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        int maxY = protocolVersion >= MultiVersionCompat.V1_17 ? level.getMinY() + level.dimensionType().logicalHeight() - 1 : level.getMaxY();

        for (int attempt = 0; attempt < 16; attempt++) {
            double x = pos.x() + (rand.nextDouble() - 0.5) * diameter;
            double yOffset = protocolVersion >= MultiVersionCompat.V1_21_2 ? (rand.nextDouble() - 0.5) * diameter : rand.nextInt(16) - 8;
            double y = Mth.clamp(pos.y() + yOffset, level.getMinY(), maxY);
            double z = pos.z() + (rand.nextDouble() - 0.5) * diameter;
            Vec3 landingPos = getTeleportDestination(player, level, x, y, z);
            if (landingPos != null) {
                return landingPos;
            }
        }
        return null;
    }

    /**
     * Simulates the teleportation function from LivingEntity
     *
     * @return The position the player teleports to, or {@code null} if the teleport fails
     * @see net.minecraft.world.entity.LivingEntity#randomTeleport(double, double, double, boolean, Predicate)
     */
    @Nullable
    private static Vec3 getTeleportDestination(LocalPlayer player, Level level, double x, double y, double z) {
        boolean usesTeleportTags = MultiVersionCompat.INSTANCE.getProtocolVersion() >= MultiVersionCompat.V26_3;
        if (usesTeleportTags) {
            Vec3 clamped = level.getWorldBorder().clampVec3ToBound(x, y, z);
            x = clamped.x();
            z = clamped.z();
        }

        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        if (!level.hasChunkAt(pos)) {
            return null;
        }

        while (pos.getY() > level.getMinY()) {
            pos.move(Direction.DOWN);
            BlockState state = level.getBlockState(pos);
            boolean isGround = state.is(usesTeleportTags ? BlockTags.ENTITIES_CAN_TELEPORT_TO : BlockTags.BLOCKS_MOTION);
            if (isGround) {
                if (usesTeleportTags && state.is(BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO)) {
                    return null;
                }
                AABB boundingBox = player.getDimensions(player.getPose()).makeBoundingBox(x, y, z);
                if (!level.noCollision(player, boundingBox) || level.containsAnyLiquid(boundingBox)) {
                    return null;
                }
                if (usesTeleportTags && level.findBlocksIn(boundingBox).filterState(blockState -> blockState.is(BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO)).anyMatched()) {
                    return null;
                }
                return new Vec3(x, y, z);
            }
            y--;
        }

        return null;
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
}
