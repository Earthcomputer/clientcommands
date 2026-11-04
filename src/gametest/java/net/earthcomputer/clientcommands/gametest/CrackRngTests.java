package net.earthcomputer.clientcommands.gametest;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import static net.earthcomputer.clientcommands.gametest.ClientCommandsClientGameTest.*;

@SuppressWarnings("UnstableApiUsage")
final class CrackRngTests {
    @TestCase
    private static void testCrackRngSurvival(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            give(singleplayerContext, Items.COBBLESTONE, 64);
            singleplayerContext.getConnection().waitForClientboundPackets();
            crackRng(context, singleplayerContext);
        });
    }

    @TestCase
    private static void testCrackRngCreative(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().setGameMode(GameType.CREATIVE));
            singleplayerContext.getConnection().waitForClientboundPackets();
            crackRng(context, singleplayerContext);
        });
    }

    @TestCase
    private static void testCrackRngThenMaintain(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            give(singleplayerContext, Items.COBBLESTONE, 64);
            singleplayerContext.getConnection().waitForClientboundPackets();
            crackRng(context, singleplayerContext);

            // the player RNG should still be known after throwing some more items
            for (int i = 0; i < 5; i++) {
                context.getInput().pressKey(options -> options.keyDrop);
            }
            singleplayerContext.getConnection().waitForServerboundPackets();
            assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
            assertPlayerSeedInSync(context, singleplayerContext);
        });
    }

    private static void crackRng(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runClientCommand(context, "ccrackrng");
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKING);
        context.waitFor(_ -> Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKING, 30 * SharedConstants.TICKS_PER_SECOND);
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
        assertPlayerSeedInSync(context, singleplayerContext);
    }
}
