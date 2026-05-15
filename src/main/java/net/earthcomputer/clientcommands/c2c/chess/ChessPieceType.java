package net.earthcomputer.clientcommands.c2c.chess;

import org.joml.Vector2i;

import java.util.List;

public enum ChessPieceType {
    KING('K') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        tryAddMove(game, x, y, dx, dy, moves);
                    }
                }
            }

            if (game.colorToMove == ChessColor.WHITE) {
                if (game.canWhiteCastleKingside) {
                    if (game.getPiece(5, 0) == null && game.getPiece(6, 0) == null) {
                        moves.add(new ChessMove(new Vector2i(4, 0), new Vector2i(6, 0), ChessMove.Type.CASTLE));
                    }
                }
                if (game.canWhiteCastleQueenside) {
                    if (game.getPiece(3, 0) == null && game.getPiece(2, 0) == null && game.getPiece(1, 0) == null) {
                        moves.add(new ChessMove(new Vector2i(4, 0), new Vector2i(2, 0), ChessMove.Type.CASTLE));
                    }
                }
            } else {
                if (game.canBlackCastleKingside) {
                    if (game.getPiece(5, 7) == null && game.getPiece(6, 7) == null) {
                        moves.add(new ChessMove(new Vector2i(4, 7), new Vector2i(6, 7), ChessMove.Type.CASTLE));
                    }
                }
                if (game.canBlackCastleQueenside) {
                    if (game.getPiece(3, 7) == null && game.getPiece(2, 7) == null && game.getPiece(1, 7) == null) {
                        moves.add(new ChessMove(new Vector2i(4, 7), new Vector2i(2, 7), ChessMove.Type.CASTLE));
                    }
                }
            }
        }
    },
    QUEEN('Q') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            BISHOP.generateMoves(game, x, y, moves);
            ROOK.generateMoves(game, x, y, moves);
        }
    },
    BISHOP('B') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            slide(game, x, y, -1, -1, moves);
            slide(game, x, y, 1, -1, moves);
            slide(game, x, y, -1, 1, moves);
            slide(game, x, y, 1, 1, moves);
        }
    },
    KNIGHT('N') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            tryAddMove(game, x, y, -2, -1, moves);
            tryAddMove(game, x, y, -2, 1, moves);
            tryAddMove(game, x, y, 2, -1, moves);
            tryAddMove(game, x, y, 2, 1, moves);
            tryAddMove(game, x, y, -1, -2, moves);
            tryAddMove(game, x, y, -1, 2, moves);
            tryAddMove(game, x, y, 1, -2, moves);
            tryAddMove(game, x, y, 1, 2, moves);
        }
    },
    ROOK('R') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            slide(game, x, y, -1, 0, moves);
            slide(game, x, y, 1, 0, moves);
            slide(game, x, y, 0, -1, moves);
            slide(game, x, y, 0, 1, moves);
        }
    },
    PAWN('P') {
        @Override
        public void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves) {
            int dy = game.colorToMove == ChessColor.WHITE ? 1 : -1;

            for (int dx = -1; dx <= 1; dx += 2) {
                if (x + dx < 0 || x + dx >= 8) {
                    continue;
                }

                // captures
                ChessPiece diagonalPiece = game.getPiece(x + dx, y + dy);
                if (diagonalPiece != null && diagonalPiece.color() != game.colorToMove) {
                    addPromotableMove(x, y, x + dx, y + dy, moves);
                }

                // en passant
                ChessPiece adjacentPiece = game.getPiece(x + dx, y);
                if (adjacentPiece != null && adjacentPiece.type() == PAWN && adjacentPiece.color() != game.colorToMove) {
                    if (!game.moveList.isEmpty()) {
                        ChessMove lastMove = game.moveList.getLast();
                        if (lastMove.to().x == x + dx && game.moveList.getLast().to().y == y && Math.abs(game.moveList.getLast().to().y - game.moveList.getLast().from().y) == 2) {
                            moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(x + dx, y + dy), ChessMove.Type.EN_PASSANT));
                        }
                    }
                }
            }

            // forward moves
            if (game.getPiece(x, y + dy) == null) {
                addPromotableMove(x, y, x, y + dy, moves);

                // first move
                if (game.colorToMove == ChessColor.WHITE ? y == 1 : y == 6) {
                    if (game.getPiece(x, y + dy * 2) == null) {
                        moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(x, y + dy * 2)));
                    }
                }
            }
        }

        private static void addPromotableMove(int x, int y, int toX, int toY, List<ChessMove> moves) {
            if (toY == 0 || toY == 7) {
                moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY), ChessMove.Type.PROMOTE_QUEEN));
                moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY), ChessMove.Type.PROMOTE_ROOK));
                moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY), ChessMove.Type.PROMOTE_BISHOP));
                moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY), ChessMove.Type.PROMOTE_KNIGHT));
            } else {
                moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY)));
            }
        }
    },
    ;

    static final ChessPieceType[] VALUES = values();

    public final char notation;

    ChessPieceType(char notation) {
        this.notation = notation;
    }

    public abstract void generateMoves(ChessGame game, int x, int y, List<ChessMove> moves);

    private static void slide(ChessGame game, int x, int y, int dx, int dy, List<ChessMove> moves) {
        for (int toX = x + dx, toY = y + dy; toX >= 0 && toX < 8 && toY >= 0 && toY < 8; toX += dx, toY += dy) {
            ChessPiece toPiece = game.getPiece(toX, toY);
            if (toPiece != null && toPiece.color() == game.colorToMove) {
                break;
            }
            moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY)));
            if (toPiece != null) {
                break;
            }
        }
    }

    private static void tryAddMove(ChessGame game, int x, int y, int dx, int dy, List<ChessMove> moves) {
        int toX = x + dx;
        int toY = y + dy;
        if (toX < 0 || toX >= 8 || toY < 0 || toY >= 8) {
            return;
        }

        ChessPiece toPiece = game.getPiece(toX, toY);
        if (toPiece == null || toPiece.color() != game.colorToMove) {
            moves.add(new ChessMove(new Vector2i(x, y), new Vector2i(toX, toY)));
        }
    }
}
