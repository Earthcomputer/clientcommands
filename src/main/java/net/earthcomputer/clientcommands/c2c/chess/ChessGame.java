package net.earthcomputer.clientcommands.c2c.chess;

import net.minecraft.client.network.PlayerListEntry;
import org.joml.Vector2i;

public class ChessGame {

    private final ChessBoard board;
    private final PlayerListEntry opponent;
    private final ChessTeam team;
    private ChessTeam teamToPlay = ChessTeam.WHITE;

    public ChessGame(ChessBoard board, PlayerListEntry opponent, ChessTeam team) {
        this.board = board;
        this.opponent = opponent;
        this.team = team;
    }

    public ChessBoard getBoard() {
        return this.board;
    }

    public PlayerListEntry getOpponent() {
        return this.opponent;
    }

    public ChessTeam getChessTeam() {
        return this.team;
    }

    public boolean move(ChessPiece piece, Vector2i target) {
        if (piece == null) {
            return false;
        }
        if (piece.team == this.teamToPlay) {
            if (this.board.tryMove(piece, target)) {
                this.teamToPlay = this.teamToPlay.other();
                return true;
            }
        }
        return false;
    }
}
