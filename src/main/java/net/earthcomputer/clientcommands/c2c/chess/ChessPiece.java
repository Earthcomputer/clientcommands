package net.earthcomputer.clientcommands.c2c.chess;

import net.minecraft.resources.Identifier;

public enum ChessPiece {
    WHITE_KING("white_king", 0.5f, 0),
    WHITE_QUEEN("white_queen"),
    WHITE_BISHOP("white_bishop"),
    WHITE_KNIGHT("white_knight"),
    WHITE_ROOK("white_rook"),
    WHITE_PAWN("white_pawn"),
    BLACK_KING("black_king", 0.5f, 0),
    BLACK_QUEEN("black_queen"),
    BLACK_BISHOP("black_bishop"),
    BLACK_KNIGHT("black_knight"),
    BLACK_ROOK("black_rook"),
    BLACK_PAWN("black_pawn"),
    ;

    public final Identifier texture;
    public final float u;
    public final float v;

    ChessPiece(String texture) {
        this(texture, 0, 0);
    }

    ChessPiece(String texture, float u, float v) {
        this.texture = Identifier.fromNamespaceAndPath("clientcommands", "textures/chess/" + texture + ".png");
        this.u = u;
        this.v = v;
    }

    public ChessColor color() {
        return ordinal() < 6 ? ChessColor.WHITE : ChessColor.BLACK;
    }

    public ChessPieceType type() {
        return ChessPieceType.VALUES[ordinal() % 6];
    }
}
