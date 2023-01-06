package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class RookChessPiece extends ChessPiece {

    boolean isOnStartingSquare;

    public RookChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
        this.isOnStartingSquare = true;
    }

    @Override
    protected boolean isValidMove(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i());
        return difference.x == 0 ^ difference.y == 0;
    }

    @Override
    protected boolean isPieceInBetween(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i());
        if (difference.x == 0) {
            int start = Math.min(this.getPosition().y, target.y);
            int end = Math.max(this.getPosition().y, target.y);
            for (int y = start + 1; y < end; y++) {
                if (this.board.getPieceAt(this.getPosition().x, y) != null) {
                    return true;
                }
            }
        } else {
            int start = Math.min(this.getPosition().x, target.x);
            int end = Math.max(this.getPosition().x, target.x);
            for (int x = start + 1; x < end; x++) {
                if (this.board.getPieceAt(x, this.getPosition().y) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void moveTo(Vector2i target) {
        super.moveTo(target);
        this.isOnStartingSquare = false;
    }

    @Override
    public String getName() {
        return "rook";
    }

    @Override
    public int getTextureStart() {
        return 0;
    }
}
