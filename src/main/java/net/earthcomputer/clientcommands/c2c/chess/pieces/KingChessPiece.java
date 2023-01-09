package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class KingChessPiece extends ChessPiece {

    private boolean isOnStartingSquare;

    public KingChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
        this.isOnStartingSquare = true;
    }

    @Override
    protected boolean isValidMove(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i());
        Vector2i absDifference = difference.absolute(new Vector2i());
        if (!this.isOnStartingSquare) {
            return Math.max(absDifference.x, absDifference.y) == 1;
        }
        int y = this.team == ChessTeam.WHITE ? 0 : 7;
        if (difference.x == -2) {
            for (int x = target.x; x <= this.getPosition().x; x++) {
                if (this.moveExposesToCheck(new Vector2i(x, y))) {
                    return false;
                }
            }
            return true;
        }
        if (difference.x == 2) {
            for (int x = this.getPosition().x; x <= target.x; x++) {
                if (this.moveExposesToCheck(new Vector2i(x, y))) {
                    return false;
                }
            }
            return true;
        }
        return Math.max(difference.x, difference.y) == 1;
    }

    @Override
    public boolean isValidAttackMove(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i()).absolute();
        return Math.max(difference.x, difference.y) == 1;
    }

    @Override
    protected boolean isPieceInBetween(Vector2i target) {
        if (this.isOnStartingSquare) {
            Vector2i difference = target.sub(this.getPosition(), new Vector2i());
            if (difference.x == -2) {
                return this.board.getPieceAt(this.getPosition().sub(1, 0, new Vector2i())) != null;
            }
            if (difference.x == 2) {
                return this.board.getPieceAt(this.getPosition().add(1, 0, new Vector2i())) != null;
            }
        }
        return false;
    }

    @Override
    public void moveTo(Vector2i target) {
        Vector2i difference = target.sub(this.getPosition(), new Vector2i());
        int y = this.team == ChessTeam.WHITE ? 0 : 7;
        if (difference.x == -2) {
            if (this.board.getPieceAt(0, y) instanceof RookChessPiece rook) {
                if (rook.isOnStartingSquare) {
                    rook.moveTo(new Vector2i(3, y));
                }
            }
        } else if (difference.x == 2) {
            if (this.board.getPieceAt(7, y) instanceof RookChessPiece rook) {
                if (rook.isOnStartingSquare) {
                    rook.moveTo(new Vector2i(5, y));
                }
            }
        }
        super.moveTo(target);
        this.isOnStartingSquare = false;
    }

    public boolean isInCheck() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board.getPieceAt(x, 7 - y);
                if (piece == null) {
                    continue;
                }
                if (piece.team == this.team) {
                    continue;
                }
                if (piece.canMoveTo(this.getPosition(), false)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "king";
    }

    @Override
    public int getTextureStart() {
        return 4 * 1024;
    }
}
