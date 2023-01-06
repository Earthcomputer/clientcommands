package net.earthcomputer.clientcommands.c2c.chess;

import net.earthcomputer.clientcommands.c2c.chess.pieces.*;
import org.joml.Vector2i;

import org.jetbrains.annotations.Nullable;

public class ChessBoard {

    private final ChessPiece[][] pieces = new ChessPiece[8][8];

    private static final char[] indexToFile = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

    public ChessBoard() {
        final ChessTeam white = ChessTeam.WHITE;
        this.addPiece(0, 0, white, RookChessPiece::new);
        this.addPiece(1, 0, white, KnightChessPiece::new);
        this.addPiece(2, 0, white, BishopChessPiece::new);
        this.addPiece(3, 0, white, QueenChessPiece::new);
        this.addPiece(4, 0, white, KingChessPiece::new);
        this.addPiece(5, 0, white, BishopChessPiece::new);
        this.addPiece(6, 0, white, KnightChessPiece::new);
        this.addPiece(7, 0, white, RookChessPiece::new);
        for (int x = 0; x < 8; x++) {
            this.addPiece(x, 1, white, PawnChessPiece::new);
        }

        final ChessTeam black = ChessTeam.BLACK;
        this.addPiece(0, 7, black, RookChessPiece::new);
        this.addPiece(1, 7, black, KnightChessPiece::new);
        this.addPiece(2, 7, black, BishopChessPiece::new);
        this.addPiece(3, 7, black, QueenChessPiece::new);
        this.addPiece(4, 7, black, KingChessPiece::new);
        this.addPiece(5, 7, black, BishopChessPiece::new);
        this.addPiece(6, 7, black, KnightChessPiece::new);
        this.addPiece(7, 7, black, RookChessPiece::new);
        for (int x = 0; x < 8; x++) {
            this.addPiece(x, 6, black, PawnChessPiece::new);
        }
    }

    public <P extends ChessPiece> void addPiece(int x, int y, ChessTeam team, ChessPieceFactory<P> factory) {
        this.pieces[x][y] = factory.create(this, team, new Vector2i(x, y));
    }

    public void addPiece(ChessPiece piece) {
        Vector2i position = piece.getPosition();
        this.pieces[position.x][position.y] = piece;
    }

    ChessPiece[][] getPieces() {
        return this.pieces;
    }

    public KingChessPiece getKing(ChessTeam team) {
        for (ChessPiece[] file : this.pieces) {
            for (ChessPiece piece : file) {
                if (piece instanceof KingChessPiece king) {
                    if (king.team == team) {
                        return king;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public ChessPiece getPieceAt(Vector2i square) {
        return this.getPieceAt(square.x, square.y);
    }

    @Nullable
    public ChessPiece getPieceAt(int x, int y) {
        return this.pieces[x][y];
    }

    public boolean isOccupied(Vector2i square) {
        return this.getPieceAt(square) != null;
    }

    /**
     * Try to move a piece.
     * @param piece the piece to move
     * @param target the square to move to
     * @return {@code true} if the move was successful, {@code false} otherwise
     */
    public boolean tryMove(ChessPiece piece, Vector2i target) {
        if (piece.canMoveTo(target)) {
            piece.moveTo(target);
            return true;
        }
        return false;
    }

    public static char indexToFile(int x) {
        return indexToFile[x];
    }
}

@FunctionalInterface
interface ChessPieceFactory<T extends ChessPiece> {
    T create(ChessBoard board, ChessTeam team, Vector2i position);
}
