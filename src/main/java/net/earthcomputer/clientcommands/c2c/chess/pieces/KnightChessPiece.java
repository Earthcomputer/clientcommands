package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class KnightChessPiece extends ChessPiece {

    public KnightChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
    }

    @Override
    protected boolean isValidMove(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i()).absolute();
        if (difference.x == 1) {
            return difference.y == 2;
        }
        if (difference.y == 1) {
            return difference.x == 2;
        }
        return false;
    }

    @Override
    protected boolean isPieceInBetween(Vector2i target) {
        return false;
    }

    @Override
    public String getName() {
        return "knight";
    }

    @Override
    public int getTextureStart() {
        return 1024;
    }
}
