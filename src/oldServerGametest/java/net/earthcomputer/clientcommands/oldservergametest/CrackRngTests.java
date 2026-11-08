package net.earthcomputer.clientcommands.oldservergametest;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.Items;

import java.util.Objects;

import static net.earthcomputer.clientcommands.oldservergametest.OldServerClientGameTest.*;

@SuppressWarnings("UnstableApiUsage")
final class CrackRngTests {
    @TestCase
    private static void testCrackRng(ClientGameTestContext context, OldServer server) {
        server.runCommand("give " + server.playerName() + " minecraft:cobblestone 64");
        context.waitFor(client -> Objects.requireNonNull(client.player).getInventory().countItem(Items.COBBLESTONE) == 64);

        runClientCommand(context, "ccrackrng");
        context.waitFor(_ -> Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKING, 30 * SharedConstants.TICKS_PER_SECOND);
        PlayerRandCracker.CrackState crackState = context.computeOnClient(_ -> Configs.playerCrackState);
        assertTrue(crackState == PlayerRandCracker.CrackState.CRACKED, () -> "Expected player crack state CRACKED, got " + crackState);

        long serverSeed = getServerPlayerSeed(server);
        long clientSeed = context.computeOnClient(_ -> PlayerRandCracker.getSeed());
        assertTrue(serverSeed == clientSeed, () -> "Client player seed doesn't match the server: server=0x" + Long.toHexString(serverSeed) + ", client=0x" + Long.toHexString(clientSeed));
    }
}
