package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.earthcomputer.clientcommands.features.EnchantmentCracker.EnumCrackState.CRACKED_PLAYER_SEED;
import static net.earthcomputer.clientcommands.features.EnchantmentCracker.MULTIPLIER;
import static net.minecraft.server.command.CommandManager.literal;

public class CrackRNGCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccrackrng");

        dispatcher.register(literal("ccrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(ServerCommandSource source) {
        SeedCracker.crack(seed -> {
            sendFeedback(new TranslatableText("commands.ccrackrng.success", Long.toHexString(seed)));
            EnchantmentCracker.playerRand.setSeed(seed ^ MULTIPLIER);
            TempRules.enchCrackState=CRACKED_PLAYER_SEED;
        });
        return 0;
    }

}
