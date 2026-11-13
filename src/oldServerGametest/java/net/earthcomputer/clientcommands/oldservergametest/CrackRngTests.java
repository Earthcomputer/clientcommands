package net.earthcomputer.clientcommands.oldservergametest;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;

import static net.earthcomputer.clientcommands.oldservergametest.OldServerClientGameTest.*;

@SuppressWarnings("UnstableApiUsage")
final class CrackRngTests {
    @TestCase
    private static void testCrackRng(ClientGameTestContext context, OldServer server) {
        runTest(context, server, () -> {
            giveItemsToThrow(context, server);

            runClientCommand(context, "ccrackrng");
            context.waitFor(_ -> Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKING, 30 * SharedConstants.TICKS_PER_SECOND);
            assertPlayerCracked(context, "ccrackrng");
            assertPlayerSeedMatchesServer(context, server);
        });
    }
}
