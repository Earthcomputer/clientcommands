package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class BishopChessPiece extends ChessPiece {

    public BishopChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
    }

    @Override
    protected boolean isValidMove(Vector2i target) {
        if (!super.isValidMove(target)) {
            return false;
        }
        Vector2i difference = target.sub(this.getPosition(), new Vector2i()).absolute();
        return difference.x == difference.y;
    }

    @Override
    protected boolean isPieceInBetween(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i());
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

    @Override
    public String getName() {
        return "bishop";
    }

    @Override
    public int getTextureStart() {
        return 2 * 1024;
    }
}
