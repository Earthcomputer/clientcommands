package net.earthcomputer.clientcommands.c2c.chess;

public enum ChessColor {
    WHITE, BLACK;

    public ChessColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
