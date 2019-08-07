package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CheatCrackRNGCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccheatcrackrng");

        dispatcher.register(literal("ccheatcrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(ServerCommandSource source) {
        long seed = EnchantmentCracker.singlePlayerCrackRNG();
        sendFeedback(new TranslatableText("commands.ccrackrng.success", Long.toHexString(seed)));
        return (int) seed;
    }

}
