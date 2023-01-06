package net.earthcomputer.clientcommands.c2c.chess;

import org.joml.Vector2i;

public abstract class ChessPiece {

    protected final ChessBoard board;
    public final ChessTeam team;
    /**
     * This position is assigned to upon creation.
     * It is always synchronised with the board indices.
     */
    private Vector2i position;

    public ChessPiece(ChessBoard board, ChessTeam team, Vector2i position) {
        this.board = board;
        this.team = team;
        this.position = position;
    }

    /**
     * The method to call to determine whether a move can be played.
     * @param target the target square
     * @return {@code true} if the piece can move to this square, {@code false} otherwise
     */
    public final boolean canMoveTo(Vector2i target) {
        return this.canMoveTo(target, true);
    }

    public final boolean canMoveTo(Vector2i target, boolean checkCheckExposure) {
        ChessPiece pieceAtTarget = this.board.getPieceAt(target);
        if (pieceAtTarget == null) {
            if (!this.isValidMove(target)) {
                return false;
            }
        } else {
            if (!this.canCapturePiece(pieceAtTarget)) {
                return false;
            }
            if (!this.isValidAttackMove(target)) {
                return false;
            }
        }
        if (this.isPieceInBetween(target)) {
            return false;
        }
        if (checkCheckExposure) {
            return !this.moveExposesToCheck(target);
        }
        return true;
    }

    /**
     * Checks if a move is valid.
     * @param target the target square
     * @return {@code true} if the move is generally valid for this piece, {@code false} otherwise
     */
    protected boolean isValidMove(Vector2i target) {
        return this.getPosition() != target;
    }

    /**
     * Checks if an attack is valid.
     * This method is the same as {@link #isValidMove} for almost all pieces
     * @param target the target square
     * @return {@code true} if the attack is generally valid for this piece, {@code false} otherwise
     */
    protected boolean isValidAttackMove(Vector2i target) {
        return this.isValidMove(target);
    }

    protected boolean canCapturePiece(ChessPiece target) {
        return this.team != target.team;
    }

    protected abstract boolean isPieceInBetween(Vector2i target);

    public boolean moveExposesToCheck(Vector2i target) {
        Vector2i oldPosition = this.getPosition();
        this.setPosition(target);
        boolean inCheck = this.board.getKing(this.team).isInCheck();
        this.setPosition(oldPosition);
        return inCheck;
    }

    public void moveTo(Vector2i target) {
        this.setPosition(target);
    }

    public Vector2i getPosition() {
        return this.position;
    }

    public void setPosition(Vector2i position) {
        ChessPiece[][] boardArray = this.board.getPieces();
        Vector2i currentPosition = this.getPosition();
        this.position = position;
        boardArray[currentPosition.x][currentPosition.y] = null;
        boardArray[position.x][position.y] = this;
    }

    public abstract String getName();

    /**
     * The pieces are based on assets from <a href="https://opengameart.org/content/chess-pieces-and-board-squares">opengameart.org</a>.
     */
    public abstract int getTextureStart();
}
