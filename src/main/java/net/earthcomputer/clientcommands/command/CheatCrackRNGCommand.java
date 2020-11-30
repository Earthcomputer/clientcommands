package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.minecraft.server.command.CommandManager.literal;

public class CheatCrackRNGCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccheatcrackrng");

        dispatcher.register(literal("ccheatcrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(ServerCommandSource source) {
        long seed;
        if (TempRules.playerCrackState.knowsSeed()) {
            long oldSeed = PlayerRandCracker.getSeed();
            seed = PlayerRandCracker.singlePlayerCrackRNG();
            sendFeedback(new TranslatableText("commands.ccheatcrackrng.success", Long.toHexString(oldSeed), Long.toHexString(seed)));
        } else {
            seed = PlayerRandCracker.singlePlayerCrackRNG();
            sendFeedback(new TranslatableText("commands.ccrackrng.success", Long.toHexString(seed)));
        }
        return (int) seed;
    }

}
