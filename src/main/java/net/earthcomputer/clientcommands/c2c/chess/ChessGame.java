package net.earthcomputer.clientcommands.c2c.chess;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChessGame {
    public final PlayerInfo opponent;
    public final ChessColor yourColor;

    private final @Nullable ChessPiece[] pieces = new ChessPiece[64];
    public boolean canWhiteCastleKingside = true;
    public boolean canWhiteCastleQueenside = true;
    public boolean canBlackCastleKingside = true;
    public boolean canBlackCastleQueenside = true;
    public ChessColor colorToMove = ChessColor.WHITE;

    private final List<RepetitionState> repetitionStates = new ArrayList<>();
    private int fiftyMoveCounter = 0;
    @Nullable
    public ChessColor drawOfferedBy = null;

    public final List<ChessMove> moveList = new ArrayList<>();
    public final List<String> algebraicMoveList = new ArrayList<>();
    public final List<ChessGame> previousGameStates = new ArrayList<>();

    public ChessGame(PlayerInfo opponent, ChessColor yourColor) {
        this.opponent = opponent;
        this.yourColor = yourColor;

        setPiece(0, 0, ChessPiece.WHITE_ROOK);
        setPiece(1, 0, ChessPiece.WHITE_KNIGHT);
        setPiece(2, 0, ChessPiece.WHITE_BISHOP);
        setPiece(3, 0, ChessPiece.WHITE_QUEEN);
        setPiece(4, 0, ChessPiece.WHITE_KING);
        setPiece(5, 0, ChessPiece.WHITE_BISHOP);
        setPiece(6, 0, ChessPiece.WHITE_KNIGHT);
        setPiece(7, 0, ChessPiece.WHITE_ROOK);
        setPiece(0, 7, ChessPiece.BLACK_ROOK);
        setPiece(1, 7, ChessPiece.BLACK_KNIGHT);
        setPiece(2, 7, ChessPiece.BLACK_BISHOP);
        setPiece(3, 7, ChessPiece.BLACK_QUEEN);
        setPiece(4, 7, ChessPiece.BLACK_KING);
        setPiece(5, 7, ChessPiece.BLACK_BISHOP);
        setPiece(6, 7, ChessPiece.BLACK_KNIGHT);
        setPiece(7, 7, ChessPiece.BLACK_ROOK);

        for (int x = 0; x < 8; x++) {
            setPiece(x, 1, ChessPiece.WHITE_PAWN);
            setPiece(x, 6, ChessPiece.BLACK_PAWN);
        }
    }

    public ChessGame(ChessGame other) {
        this.opponent = other.opponent;
        this.yourColor = other.yourColor;
        System.arraycopy(other.pieces, 0, pieces, 0, pieces.length);
        this.canWhiteCastleKingside = other.canWhiteCastleKingside;
        this.canWhiteCastleQueenside = other.canWhiteCastleQueenside;
        this.canBlackCastleKingside = other.canBlackCastleKingside;
        this.canBlackCastleQueenside = other.canBlackCastleQueenside;
        this.colorToMove = other.colorToMove;
        this.repetitionStates.addAll(other.repetitionStates);
        this.fiftyMoveCounter = other.fiftyMoveCounter;
        this.drawOfferedBy = other.drawOfferedBy;
        this.moveList.addAll(other.moveList);
        this.algebraicMoveList.addAll(other.algebraicMoveList);
        this.previousGameStates.addAll(other.previousGameStates);
    }

    @Nullable
    public ChessPiece getPiece(int x, int y) {
        return pieces[x + y * 8];
    }

    public void setPiece(int x, int y, @Nullable ChessPiece piece) {
        pieces[x + y * 8] = piece;
    }

    @Nullable
    public ChessPiece getPieceInPreviousPosition(int x, int y) {
        return repetitionStates.isEmpty() ? null : repetitionStates.getLast().board[x + y * 8];
    }

    public boolean isCheck() {
        // find the king
        ChessPiece kingPiece = colorToMove == ChessColor.WHITE ? ChessPiece.WHITE_KING : ChessPiece.BLACK_KING;
        int kingX;
        int kingY = -1;
        kingLoop:
        for (kingX = 0; kingX < 8; kingX++) {
            for (kingY = 0; kingY < 8; kingY++) {
                if (getPiece(kingX, kingY) == kingPiece) {
                    break kingLoop;
                }
            }
        }

        ChessGame oppositeToMoveGame = new ChessGame(this);
        oppositeToMoveGame.colorToMove = colorToMove.opposite();
        for (ChessMove move : oppositeToMoveGame.semiLegalMoves()) {
            if (move.to().x == kingX && move.to().y == kingY) {
                return true;
            }
        }

        return false;
    }

    public boolean isCheckmate() {
        return isCheck() && legalMoves().isEmpty();
    }

    public boolean isStalemate() {
        return !isCheck() && legalMoves().isEmpty();
    }

    public boolean isDrawByFiftyMoves() {
        return fiftyMoveCounter >= 100; // 50 moves = 100 ply
    }

    public boolean isDrawByRepetition() {
        RepetitionState repetitionState = currentRepetitionState();
        int repetitions = 0;
        for (int i = repetitionStates.size() - 1; i >= 0; i--) {
            if (repetitionStates.get(i).equals(repetitionState)) {
                repetitions++;
                if (repetitions >= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isDrawByInsufficientMaterial() {
        // https://www.reddit.com/r/chess/comments/se89db/a_writeup_on_definitions_of_insufficient_material/

        int whiteKnights = 0;
        int blackKnights = 0;
        int whiteLightSquaredBishops = 0;
        int whiteDarkSquaredBishops = 0;
        int blackLightSquaredBishops = 0;
        int blackDarkSquaredBishops = 0;

        for (int i = 0; i < pieces.length; i++) {
            switch (pieces[i]) {
                case WHITE_PAWN, BLACK_PAWN, WHITE_ROOK, BLACK_ROOK, WHITE_QUEEN, BLACK_QUEEN -> {
                    return false;
                }
                case WHITE_KNIGHT -> whiteKnights++;
                case BLACK_KNIGHT -> blackKnights++;
                case WHITE_BISHOP -> {
                    if (i % 2 == i / 8 % 2) {
                        whiteDarkSquaredBishops++;
                    } else {
                        whiteLightSquaredBishops++;
                    }
                }
                case BLACK_BISHOP -> {
                    if (i % 2 == i / 8 % 2) {
                        blackDarkSquaredBishops++;
                    } else {
                        blackLightSquaredBishops++;
                    }
                }
                case null, default -> {
                }
            }
        }

        if (whiteKnights >= 2 || blackKnights >= 2) {
            return false;
        }
        if (whiteLightSquaredBishops > 0 && whiteDarkSquaredBishops > 0) {
            return false;
        }
        if (blackLightSquaredBishops > 0 && blackDarkSquaredBishops > 0) {
            return false;
        }
        if (whiteKnights > 0 && (whiteLightSquaredBishops > 0 || whiteDarkSquaredBishops > 0)) {
            return false;
        }
        if (blackKnights > 0 && (blackLightSquaredBishops > 0 || blackDarkSquaredBishops > 0)) {
            return false;
        }
        if (whiteKnights > 0 && (blackKnights > 0 || blackLightSquaredBishops > 0 || blackDarkSquaredBishops > 0)) {
            return false;
        }
        if (blackKnights > 0 && (whiteKnights > 0 || whiteLightSquaredBishops > 0 || whiteDarkSquaredBishops > 0)) {
            return false;
        }

        return true;
    }

    @Nullable
    public Component detectEndCondition() {
        if (isCheckmate()) {
            if (colorToMove == yourColor) {
                return Component.translatable("chessGame.checkmate.lost");
            } else {
                return Component.translatable("chessGame.checkmate.won");
            }
        }
        if (isStalemate()) {
            return Component.translatable("chessGame.draw.stalemate");
        }
        if (isDrawByInsufficientMaterial()) {
            return Component.translatable("chessGame.draw.insufficientMaterial");
        }
        if (isDrawByRepetition()) {
            return Component.translatable("chessGame.draw.repetition");
        }
        if (isDrawByFiftyMoves()) {
            return Component.translatable("chessGame.draw.fiftyMoves");
        }

        return null;
    }

    // Returns the list of legal moves, disregarding whether it puts your king in check (or through check if castling)
    private List<ChessMove> semiLegalMoves() {
        List<ChessMove> moves = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = getPiece(x, y);
                if (piece != null && piece.color() == colorToMove) {
                    piece.type().generateMoves(this, x, y, moves);
                }
            }
        }
        return moves;
    }

    public List<ChessMove> legalMoves() {
        List<ChessMove> moves = semiLegalMoves();
        moves.removeIf(move -> !move.type().isLegalAccordingToCheck(this, move));
        return moves;
    }

    public boolean isCapture(ChessMove move) {
        ChessPiece targetPiece = getPiece(move.to().x, move.to().y);
        return targetPiece != null || move.type() == ChessMove.Type.EN_PASSANT;
    }

    private RepetitionState currentRepetitionState() {
        boolean canEnPassant = false;
        for (ChessMove testMove : semiLegalMoves()) {
            if (testMove.type() == ChessMove.Type.EN_PASSANT) {
                canEnPassant = true;
                break;
            }
        }

        return new RepetitionState(pieces.clone(), canWhiteCastleKingside, canWhiteCastleQueenside, canBlackCastleKingside, canBlackCastleQueenside, canEnPassant);
    }

    public void makeMove(ChessMove move) {
        ChessPiece piece = getPiece(move.from().x, move.from().y);
        if (piece == null) {
            return;
        }

        String algebraicNotation = getAlgebraicNotation(move);
        ChessGame prevGameState = new ChessGame(this);

        if (piece.type() == ChessPieceType.PAWN || isCapture(move)) {
            fiftyMoveCounter = 0;
        } else {
            fiftyMoveCounter++;
        }

        repetitionStates.add(currentRepetitionState());

        move.perform(this);

        if (piece == ChessPiece.WHITE_KING) {
            canWhiteCastleKingside = false;
            canWhiteCastleQueenside = false;
        } else if (piece == ChessPiece.BLACK_KING) {
            canBlackCastleKingside = false;
            canBlackCastleQueenside = false;
        }

        if ((move.from().x == 0 && move.from().y == 0) || (move.to().x == 0 && move.to().y == 0)) {
            canWhiteCastleQueenside = false;
        } else if ((move.from().x == 7 && move.from().y == 0) || (move.to().x == 7 && move.to().y == 0)) {
            canWhiteCastleKingside = false;
        } else if ((move.from().x == 0 && move.from().y == 7) || (move.to().x == 0 && move.to().y == 7)) {
            canBlackCastleQueenside = false;
        } else if ((move.from().x == 7 && move.from().y == 7) || (move.to().x == 7 && move.to().y == 7)) {
            canBlackCastleKingside = false;
        }

        colorToMove = colorToMove.opposite();

        moveList.add(move);
        algebraicMoveList.add(algebraicNotation);
        previousGameStates.add(prevGameState);
    }

    public static String getSquareName(int x, int y) {
        return "" + (char) ('a' + x) + (y + 1);
    }

    public String getAlgebraicNotation(ChessMove move) {
        StringBuilder notation = new StringBuilder();

        if (move.type() == ChessMove.Type.CASTLE) {
            if (move.to().x > 4) {
                notation.append("0-0");
            } else {
                notation.append("0-0-0");
            }
        } else {
            ChessPiece piece = getPiece(move.from().x, move.from().y);
            ChessPieceType pieceType = piece == null ? null : piece.type();
            if (pieceType == ChessPieceType.PAWN) {
                if (move.from().x != move.to().x) {
                    notation.append((char) ('a' + move.from().x));
                }
            } else {
                notation.append(pieceType == null ? '?' : pieceType.notation);

                boolean needsDisambiguation = false;
                boolean anotherMoveFromSameX = false;
                boolean anotherMoveFromSameY = false;
                for (ChessMove legalMove : legalMoves()) {
                    if (legalMove.to().x != move.to().x || legalMove.to().y != move.to().y) {
                        continue;
                    }
                    if (legalMove.from().x == move.from().x && legalMove.from().y == move.from().y) {
                        continue;
                    }
                    ChessPiece otherPiece = getPiece(legalMove.from().x, legalMove.from().y);
                    if (otherPiece != piece) {
                        continue;
                    }

                    needsDisambiguation = true;
                    if (legalMove.from().x == move.from().x) {
                        anotherMoveFromSameX = true;
                    }
                    if (legalMove.from().y == move.from().y) {
                        anotherMoveFromSameY = true;
                    }
                }

                if (needsDisambiguation) {
                    if (!anotherMoveFromSameX) {
                        notation.append((char) ('a' + move.from().x));
                    } else if (!anotherMoveFromSameY) {
                        notation.append(move.from().y + 1);
                    } else {
                        notation.append(getSquareName(move.from().x, move.from().y));
                    }
                }
            }

            if (isCapture(move)) {
                notation.append('x');
            }

            notation.append(getSquareName(move.to().x, move.to().y));

            switch (move.type()) {
                case PROMOTE_QUEEN -> notation.append("=Q");
                case PROMOTE_ROOK -> notation.append("=R");
                case PROMOTE_BISHOP -> notation.append("=B");
                case PROMOTE_KNIGHT -> notation.append("=N");
            }
        }

        ChessGame gameCopy = new ChessGame(this);
        move.perform(gameCopy);
        gameCopy.colorToMove = gameCopy.colorToMove.opposite();
        if (gameCopy.isCheckmate()) {
            notation.append('#');
        } else if (gameCopy.isCheck()) {
            notation.append('+');
        }

        return notation.toString();
    }

    public String getFen() {
        StringBuilder fen = new StringBuilder();

        for (int y = 7; y >= 0; y--) {
            int emptyCounter = 0;
            for (int x = 0; x < 8; x++) {
                ChessPiece piece = getPiece(x, y);
                if (piece == null) {
                    emptyCounter++;
                } else {
                    if (emptyCounter > 0) {
                        fen.append(emptyCounter);
                        emptyCounter = 0;
                    }
                    if (piece.color() == ChessColor.WHITE) {
                        fen.append(piece.type().notation);
                    } else {
                        fen.append(Character.toLowerCase(piece.type().notation));
                    }
                }
            }

            if (emptyCounter > 0) {
                fen.append(emptyCounter);
            }

            if (y != 0) {
                fen.append('/');
            }
        }

        fen.append(' ');

        if (colorToMove == ChessColor.WHITE) {
            fen.append('w');
        } else {
            fen.append('b');
        }

        fen.append(' ');

        if (!canWhiteCastleKingside && !canWhiteCastleQueenside && !canBlackCastleKingside && !canBlackCastleQueenside) {
            fen.append('-');
        } else {
            if (canWhiteCastleKingside) {
                fen.append('K');
            }
            if (canWhiteCastleQueenside) {
                fen.append('Q');
            }
            if (canBlackCastleKingside) {
                fen.append('k');
            }
            if (canBlackCastleQueenside) {
                fen.append('q');
            }
        }

        fen.append(' ');

        Vector2i enPassantTargetSquare = null;
        if (!moveList.isEmpty()) {
            ChessPiece movedPiece = getPiece(moveList.getLast().to().x, moveList.getLast().to().y);
            if (movedPiece != null && movedPiece.type() == ChessPieceType.PAWN && Math.abs(moveList.getLast().to().y - moveList.getLast().from().y) == 2) {
                enPassantTargetSquare = new Vector2i(moveList.getLast().to().x, (moveList.getLast().to().y + moveList.getLast().from().y) / 2);
            }
        }

        if (enPassantTargetSquare == null) {
            fen.append('-');
        } else {
            fen.append(getSquareName(enPassantTargetSquare.x, enPassantTargetSquare.y));
        }

        fen.append(' ').append(fiftyMoveCounter).append(' ').append(moveList.size() / 2 + 1);

        return fen.toString();
    }

    public String getPgn() {
        StringBuilder pgn = new StringBuilder();

        for (int i = 0; i < moveList.size(); i += 2) {
            if (!pgn.isEmpty()) {
                pgn.append(' ');
            }
            pgn.append(i / 2 + 1).append(". ");
            pgn.append(algebraicMoveList.get(i));

            if (i + 1 < moveList.size()) {
                pgn.append(' ').append(algebraicMoveList.get(i + 1));
            }
        }

        return pgn.toString();
    }

    private record RepetitionState(@Nullable ChessPiece[] board, int flags) {
        RepetitionState(
            @Nullable ChessPiece[] board,
            boolean canWhiteCastleKingside,
            boolean canWhiteCastleQueenside,
            boolean canBlackCastleKingside,
            boolean canBlackCastleQueenside,
            boolean canEnPassant
        ) {
            this(board, buildFlags(canWhiteCastleKingside, canWhiteCastleQueenside, canBlackCastleKingside, canBlackCastleQueenside, canEnPassant));
        }

        private static int buildFlags(
            boolean canWhiteCastleKingside,
            boolean canWhiteCastleQueenside,
            boolean canBlackCastleKingside,
            boolean canBlackCastleQueenside,
            boolean canEnPassant
        ) {
            int flags = 0;
            flags |= canWhiteCastleKingside ? 1 : 0;
            flags |= canWhiteCastleQueenside ? 2 : 0;
            flags |= canBlackCastleKingside ? 4 : 0;
            flags |= canBlackCastleQueenside ? 8 : 0;
            flags |= canEnPassant ? 16 : 0;
            return flags;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(board) * 31 + flags;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RepetitionState(ChessPiece[] otherBoard, int otherFlags))) {
                return false;
            }

            return Arrays.equals(board, otherBoard) && flags == otherFlags;
        }
    }
}
