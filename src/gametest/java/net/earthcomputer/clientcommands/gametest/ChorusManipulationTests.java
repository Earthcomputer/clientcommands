package net.earthcomputer.clientcommands.gametest;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

import static net.earthcomputer.clientcommands.gametest.ClientCommandsClientGameTest.*;

// Player is at 0, 0, facing south (towards +Z)
@SuppressWarnings("UnstableApiUsage")
final class ChorusManipulationTests {
    private static final long[] SEEDS = {0x1234_5678_9abcL, 0xdead_beef_cafeL, 0x0123_4567_89abL, 0xfeed_face_b00cL};
    private static final Vec3 SPAWN_VEC = Vec3.atBottomCenterOf(SPAWN_POS);

    @TestCase
    private static void testEatChorusFruit(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // The player RNG should stay in sync when eating chorus fruit without chorus manipulation, whichever teleport attempt succeeds
        for (long seed : SEEDS) {
            runTest(context, singleplayerContext, "0x" + Long.toHexString(seed), () -> {
                give(singleplayerContext, Items.CHORUS_FRUIT);
                setKnownPlayerSeed(context, singleplayerContext, seed);
                eatChorusFruit(context, singleplayerContext);
                assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
                assertPlayerSeedInSync(context, singleplayerContext);
            });
        }
    }

    @TestCase
    private static void testRelativeBlockGoal(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            // relative goals are relative to the player's exact position
            eatWithGoal(context, singleplayerContext, "cchorus setGoal relative block 3 0 2", blockGoalArea(BlockPos.ZERO.offset(3, 0, 2)).move(SPAWN_VEC))
        );
    }

    @TestCase
    private static void testAbsoluteBlockGoal(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            BlockPos goal = SPAWN_POS.offset(-4, 0, 3);
            eatWithGoal(context, singleplayerContext, "cchorus setGoal absolute block " + goal.getX() + " " + goal.getY() + " " + goal.getZ(), blockGoalArea(goal));
        });
    }

    @TestCase
    private static void testAbsoluteAreaGoal(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            eatWithGoal(context, singleplayerContext, "cchorus setGoal absolute area 2.0 -60 -6.0 5.0 -59 -3.0", new AABB(2, -60, -6, 5, -59, -3))
        );
    }

    @TestCase
    private static void testRaisedBlockGoal(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            BlockPos goal = SPAWN_POS.offset(5, 2, -2);
            fill(singleplayerContext, goal.offset(-1, -2, -1), goal.offset(1, -1, 1), Blocks.STONE);
            eatWithGoal(context, singleplayerContext, "cchorus setGoal absolute block " + goal.getX() + " " + goal.getY() + " " + goal.getZ(), blockGoalArea(goal));
        });
    }

    @TestCase
    private static void testGoalUnderCeiling(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            // a 2 block high gap, which the player can only fit into if they teleport to the bottom of it
            BlockPos goal = SPAWN_POS.offset(-4, 0, -4);
            fill(singleplayerContext, goal.offset(-2, 2, -2), goal.offset(2, 2, 2), Blocks.STONE);
            AABB goalArea = new AABB(goal.getX() - 1, goal.getY(), goal.getZ() - 1, goal.getX() + 2, goal.getY() + 1, goal.getZ() + 2);
            eatWithGoal(context, singleplayerContext, String.format(Locale.ROOT, "cchorus setGoal absolute area %.1f %.1f %.1f %.1f %.1f %.1f",
                goalArea.minX, goalArea.minY, goalArea.minZ, goalArea.maxX, goalArea.maxY, goalArea.maxZ), goalArea);
        });
    }

    private static void eatWithGoal(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, String goalCommand, AABB expectedArea) {
        give(singleplayerContext, Items.CHORUS_FRUIT);
        give(singleplayerContext, Items.COBBLESTONE, 64 * 8);
        singleplayerContext.getConnection().waitForClientboundPackets();
        setKnownPlayerSeed(context, singleplayerContext, SEEDS[0]);
        runClientCommand(context, "cconfig clientcommands chorusManipulation set true");
        runClientCommand(context, goalCommand);
        eatChorusFruit(context, singleplayerContext);
        Vec3 pos = getServerPlayerPos(singleplayerContext);
        assertTrue(expectedArea.contains(pos), () -> "Expected the player to teleport into " + expectedArea + ", but they teleported to " + pos);
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
        assertPlayerSeedInSync(context, singleplayerContext);
    }

    private static void eatChorusFruit(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        useUntilConsumed(context, singleplayerContext, Items.CHORUS_FRUIT);
        // the teleport shows up on the server a tick after the chorus fruit is gone
        singleplayerContext.getServer().waitFor(_ -> !singleplayerContext.getConnection().getServerPlayer().position().equals(SPAWN_VEC), 20);
    }

    // The area targeted by "/cchorus setGoal ... block"
    private static AABB blockGoalArea(BlockPos goal) {
        return new AABB(goal.getX() - 0.2, goal.getY(), goal.getZ() - 0.2, goal.getX() + 1.2, goal.getY() + 1, goal.getZ() + 1.2);
    }

    private static Vec3 getServerPlayerPos(TestSingleplayerContext singleplayerContext) {
        return singleplayerContext.getServer().computeOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().position());
    }
}
