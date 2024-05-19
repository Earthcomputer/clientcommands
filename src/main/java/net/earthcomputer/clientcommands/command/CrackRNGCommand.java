package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.earthcomputer.clientcommands.features.CCrackRng;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CrackRNGCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(FabricClientCommandSource source) {
        ServerBrandManager.rngWarning();
        CCrackRng.crack(seed -> {
            source.sendFeedback(Component.translatable("commands.ccrackrng.success", Long.toHexString(seed)));
            PlayerRandCracker.setSeed(seed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        });
        return Command.SINGLE_SUCCESS;
    }

}
