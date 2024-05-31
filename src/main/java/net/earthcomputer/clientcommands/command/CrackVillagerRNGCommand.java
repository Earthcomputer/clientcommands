package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.CCrackVillager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CrackVillagerRNGCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccrackvillager")
                .executes(ctx -> crackVillagerRNG(ctx.getSource()))
                .then(literal("cancel")
                        .executes(ctx -> cancel(ctx.getSource())))
                .then(literal("interval")
                        .then(argument("interval0", IntegerArgumentType.integer(20, 100))
                                .executes(ctx -> crackWithInterval(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "interval0"))))));
    }

    private static int crackVillagerRNG(FabricClientCommandSource source) throws CommandSyntaxException {
        ServerBrandManager.rngWarning();
        CCrackVillager.crackVillager(source.getPlayer(), seed -> {
            source.sendFeedback(Component.translatable("commands.ccrackvillager.success", Long.toHexString(seed)));
            PlayerRandCracker.setSeed(seed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(FabricClientCommandSource source) {
        CCrackVillager.cancel();
        source.sendFeedback(Component.translatable("commands.ccrackvillager.cancel"));
        return Command.SINGLE_SUCCESS;
    }

    private static int crackWithInterval(FabricClientCommandSource source, int interval) throws CommandSyntaxException {
        CCrackVillager.setInterval(interval);
        return crackVillagerRNG(source);
    }
}
