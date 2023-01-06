package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.EnumArgumentType;

public class ChessTeamArgumentType extends EnumArgumentType<ChessTeam> {

    private ChessTeamArgumentType() {
        super(ChessTeam.CODEC, ChessTeam::values);
    }

    public static ChessTeamArgumentType chessTeam() {
        return new ChessTeamArgumentType();
    }

    public static ChessTeam getChessTeam(CommandContext<FabricClientCommandSource> context, String id) {
        return context.getArgument(id, ChessTeam.class);
    }
}
