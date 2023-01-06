package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class QueenChessPiece extends ChessPiece {

    public QueenChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
    }

    @Override
    protected boolean isValidMove(Vector2i target) {
        if (!super.isValidMove(target)) {
            return false;
        }
        Vector2i difference = target.sub(this.getPosition(), new Vector2i()).absolute();
        if (difference.x == 0 || difference.y == 0) {
            return true;
        }
        return difference.x == difference.y;
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
        } else if (difference.y == 0) {
            int start = Math.min(this.getPosition().x, target.x);
            int end = Math.max(this.getPosition().x, target.x);
            for (int x = start + 1; x < end; x++) {
                if (this.board.getPieceAt(x, this.getPosition().y) != null) {
                    return true;
                }
            }
        } else {
            int incrementX = Integer.signum(difference.x);
            int incrementY = Integer.signum(difference.y);
            int stop = Math.abs(difference.x);
            for (int i = 1, x = incrementX, y = incrementY; i < stop; i++, x += incrementX, y += incrementY) {
                if (this.board.getPieceAt(this.getPosition().x + x, this.getPosition().y + y) != null) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public String getName() {
        return "queen";
    }

    @Override
    public int getTextureStart() {
        return 3 * 1024;
    }
}
