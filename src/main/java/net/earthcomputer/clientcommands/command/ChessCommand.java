package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class ChessCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGame.CHESS_TYPE.createCommandTree());
    }
}
