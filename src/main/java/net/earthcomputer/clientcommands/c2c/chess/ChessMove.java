package net.earthcomputer.clientcommands.c2c.chess;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import org.joml.Vector2i;

import java.util.function.IntFunction;

public record ChessMove(Vector2i from, Vector2i to, Type type) {
    public static final StreamCodec<ByteBuf, ChessMove> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE,
        move -> (byte) ((move.from.x << 3) | move.from.y),
        ByteBufCodecs.BYTE,
        move -> (byte) ((move.to.x << 3) | move.to.y),
        Type.STREAM_CODEC,
        ChessMove::type,
        (from, to, type) -> new ChessMove(new Vector2i((from >> 3) & 7, from & 7), new Vector2i((to >> 3) & 7, to & 7), type)
    );

    public ChessMove(Vector2i from, Vector2i to) {
        this(from, to, Type.REGULAR);
    }

    public void perform(ChessGame game) {
        type.perform(game, this);
    }

    public enum Type {
        REGULAR {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                game.setPiece(move.to.x, move.to.y, game.getPiece(move.from.x, move.from.y));
                game.setPiece(move.from.x, move.from.y, null);
            }
        },
        CASTLE {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                REGULAR.perform(game, move);
                ChessMove rookMove = move.to.x > 4 ? new ChessMove(new Vector2i(7, move.to.y), new Vector2i(5, move.to.y)) : new ChessMove(new Vector2i(0, move.to.y), new Vector2i(3, move.to.y));
                REGULAR.perform(game, rookMove);
            }

            @Override
            public boolean isLegalAccordingToCheck(ChessGame game, ChessMove move) {
                if (!super.isLegalAccordingToCheck(game, move)) {
                    return false;
                }
                ChessGame throughCheckGame = new ChessGame(game);
                ChessMove kingMove = move.to.x > 4 ? new ChessMove(move.from, new Vector2i(5, move.to.y)) : new ChessMove(move.from, new Vector2i(3, move.to.y));
                REGULAR.perform(throughCheckGame, kingMove);
                return !throughCheckGame.isCheck();
            }
        },
        EN_PASSANT {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                REGULAR.perform(game, move);
                game.setPiece(move.to.x, move.from.y, null);
            }
        },
        PROMOTE_QUEEN {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                game.setPiece(move.from.x, move.from.y, null);
                game.setPiece(move.to.x, move.to.y, game.colorToMove == ChessColor.WHITE ? ChessPiece.WHITE_QUEEN : ChessPiece.BLACK_QUEEN);
            }
        },
        PROMOTE_ROOK {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                game.setPiece(move.from.x, move.from.y, null);
                game.setPiece(move.to.x, move.to.y, game.colorToMove == ChessColor.WHITE ? ChessPiece.WHITE_ROOK : ChessPiece.BLACK_ROOK);
            }
        },
        PROMOTE_BISHOP {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                game.setPiece(move.from.x, move.from.y, null);
                game.setPiece(move.to.x, move.to.y, game.colorToMove == ChessColor.WHITE ? ChessPiece.WHITE_BISHOP : ChessPiece.BLACK_BISHOP);
            }
        },
        PROMOTE_KNIGHT {
            @Override
            public void perform(ChessGame game, ChessMove move) {
                game.setPiece(move.from.x, move.from.y, null);
                game.setPiece(move.to.x, move.to.y, game.colorToMove == ChessColor.WHITE ? ChessPiece.WHITE_KNIGHT : ChessPiece.BLACK_KNIGHT);
            }
        },
        ;

        private static final IntFunction<Type> BY_ID = ByIdMap.continuous(Type::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Type> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Type::ordinal);

        public abstract void perform(ChessGame game, ChessMove move);

        public boolean isLegalAccordingToCheck(ChessGame game, ChessMove move) {
            if (move.type() == Type.CASTLE && game.isCheck()) {
                return false;
            }
            ChessGame gameCopy = new ChessGame(game);
            perform(gameCopy, move);
            return !gameCopy.isCheck();
        }

        public boolean isPromotion() {
            return this == PROMOTE_QUEEN || this == PROMOTE_ROOK || this == PROMOTE_BISHOP || this == PROMOTE_KNIGHT;
        }
    }
}
