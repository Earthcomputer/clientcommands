package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.OptionalLong;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CheatCrackRNGCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccheatcrackrng");

        dispatcher.register(literal("ccheatcrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(ServerCommandSource source) {
        OptionalLong seed = PlayerRandCracker.singlePlayerCrackRNG();
        if (!seed.isPresent()) {
            sendFeedback(new TranslatableText("commands.ccheatcrackrng.java14").formatted(Formatting.RED));
            return 0;
        }

        if (TempRules.playerCrackState.knowsSeed()) {
            long oldSeed = PlayerRandCracker.getSeed();
            sendFeedback(new TranslatableText("commands.ccheatcrackrng.success", Long.toHexString(oldSeed), Long.toHexString(seed.getAsLong())));
        } else {
            sendFeedback(new TranslatableText("commands.ccrackrng.success", Long.toHexString(seed.getAsLong())));
        }
        return (int) seed.getAsLong();
    }

}
