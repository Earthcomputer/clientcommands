package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.OptionalLong;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CheatCrackRNGCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccheatcrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(FabricClientCommandSource source) {
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
