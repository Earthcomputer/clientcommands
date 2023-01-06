package net.earthcomputer.clientcommands.c2c.chess;

import net.minecraft.util.StringIdentifiable;

public enum ChessTeam implements StringIdentifiable {

    WHITE("white"),
    BLACK("black");

    public static final com.mojang.serialization.Codec<ChessTeam> CODEC = StringIdentifiable.createCodec(ChessTeam::values);

    private final String set;

    ChessTeam(String set) {
        this.set = set;
    }

    public ChessTeam other() {
        return this == ChessTeam.WHITE ? ChessTeam.BLACK : ChessTeam.WHITE;
    }

    @Override
    public String asString() {
        return this.set;
    }
}
