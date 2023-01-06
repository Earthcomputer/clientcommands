package net.earthcomputer.clientcommands.c2c.chess.pieces;

import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import org.joml.Vector2i;

public class PawnChessPiece extends ChessPiece {

    private boolean isOnStartingSquare;

    public PawnChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        super(board, team, position);
        this.isOnStartingSquare = true;
    }

    @Override
    public boolean isValidMove(Vector2i target) {
        if (this.getPosition().x != target.x) {
            return false;
        }
        int difference;
        if (this.team == ChessTeam.WHITE) {
            difference = target.y - this.getPosition().y;
        } else {
            difference = this.getPosition().y - target.y;
        }
        if (difference == 1) {
            return true;
        }
        if (difference == 2) {
            return this.isOnStartingSquare;
        }
        return false;
    }

    @Override
    public boolean isValidAttackMove(Vector2i target) {
        if (this.getPosition().x - 1 != target.x && this.getPosition().x + 1 != target.x) {
            return false;
        }
        int difference;
        if (this.team == ChessTeam.WHITE) {
            difference = target.y - this.getPosition().y;
        } else {
            difference = this.getPosition().y - target.y;
        }
        return difference == 1;
    }

    @Override
    protected boolean isPieceInBetween(Vector2i target) {
        if (this.isOnStartingSquare) {
            Vector2i difference = target.sub(this.getPosition(), new Vector2i());
            if (difference.y == -2) {
                return this.board.getPieceAt(this.getPosition().sub(0, 1, new Vector2i())) != null;
            }
            if (difference.y == 2) {
                return this.board.getPieceAt(this.getPosition().add(0, 1, new Vector2i())) != null;
            }
        }
        return false;
    }

    @Override
    public void moveTo(Vector2i target) {
        super.moveTo(target);
        this.isOnStartingSquare = false;
        int backRank = this.team == ChessTeam.WHITE ? 7 : 0;
        if (this.getPosition().y == backRank) {
            this.board.addPiece(new QueenChessPiece(this.board, this.team, new Vector2i(this.getPosition())));
        }
    }

    @Override
    public String getName() {
        return "pawn";
    }

    @Override
    public int getTextureStart() {
        return 5 * 1024;
    }
}
