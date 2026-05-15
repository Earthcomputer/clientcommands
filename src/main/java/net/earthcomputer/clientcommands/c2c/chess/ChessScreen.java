package net.earthcomputer.clientcommands.c2c.chess;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.ChessDrawOfferC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.ChessMoveC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.ChessResignC2CPacket;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.earthcomputer.clientcommands.render.ColoredTriangleRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Matrix3x2f;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ChessScreen extends Screen {
    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/chess/board.png");
    private static final Identifier SQUARE_HIGHLIGHT_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/chess/square_highlight.png");
    private static final Identifier CAPTURE_HIGHLIGHT_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/chess/capture_highlight.png");
    private static final Identifier CHECK_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/chess/check.png");

    private static final int SQUARE_SIZE = 36;
    private static final int BOARD_SIZE = 8 * SQUARE_SIZE;
    private static final int PIECE_SIZE = 32;
    private static final int PADDING = (SQUARE_SIZE - PIECE_SIZE) / 2;
    private static final int ARROW_THICKNESS = 8;
    private static final int ARROW_HALF_THICKNESS = ARROW_THICKNESS / 2;
    private static final int ARROW_HEAD_SIZE = 10;
    private static final int BOARD_EDGE_PADDING = 15;
    private static final int RANK_FILE_LABEL_PADDING = 1;

    private static final int BLUE_HIGHLIGHT = 0xc0_03cafc;
    private static final int RED_HIGHLIGHT = 0xc0_f21b42;
    private static final int GREEN_HIGHLIGHT = 0xc0_03fc3d;
    private static final int ORANGE_HIGHLIGHT = 0xc0_f0870e;
    private static final int SELECTED_HIGHLIGHT_COLOR = 0xc0_f2d51b;

    private static final int MOVE_ANIMATION_LENGTH = 3;

    private static final SoundEvent MOVE_SOUND = SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF;
    private static final float MOVE_VOLUME = 1;
    private static final float MOVE_PITCH = 1;
    private static final SoundEvent CAPTURE_SOUND = SoundEvents.ITEM_PICKUP;
    private static final float CAPTURE_VOLUME = 0.75f;
    private static final float CAPTURE_PITCH = 1;
    private static final SoundEvent CASTLING_SOUND = SoundEvents.PISTON_CONTRACT;
    private static final float CASTLING_VOLUME = 1;
    private static final float CASTLING_PITCH = 1;
    private static final Holder<SoundEvent> CHECK_SOUND = SoundEvents.NOTE_BLOCK_BELL;
    private static final float CHECK_VOLUME = 1;
    private static final float CHECK_PITCH = 1;
    private static final SoundEvent GAME_END_SOUND = SoundEvents.PLAYER_LEVELUP;
    private static final float GAME_END_VOLUME = 1;
    private static final float GAME_END_PITCH = 1.5f;

    private static final ChessPiece[] WHITE_PROMOTION_PIECES = { ChessPiece.WHITE_QUEEN, ChessPiece.WHITE_ROOK, ChessPiece.WHITE_BISHOP, ChessPiece.WHITE_KNIGHT };
    private static final ChessPiece[] BLACK_PROMOTION_PIECES = { ChessPiece.BLACK_QUEEN, ChessPiece.BLACK_ROOK, ChessPiece.BLACK_BISHOP, ChessPiece.BLACK_KNIGHT };
    private static final ChessMove.Type[] PROMOTION_MOVE_TYPES = { ChessMove.Type.PROMOTE_QUEEN, ChessMove.Type.PROMOTE_ROOK, ChessMove.Type.PROMOTE_BISHOP, ChessMove.Type.PROMOTE_KNIGHT };

    private final ChessGame game;
    @Nullable
    private Vector2i selectedSquare;
    private boolean dragging = false;
    private boolean isSecondClick = false;
    private final Map<Vector2i, Integer> squareHighlights = new HashMap<>();
    private final Map<Arrow, Integer> arrows = new LinkedHashMap<>();
    @Nullable
    private Vector2i highlightStartSquare;
    @Nullable
    private ChessMove lastSeenMove;
    private int moveAnimationTicks = -1;
    private boolean isCastlingAnimatingRookOnly = false;
    private boolean wasGameEnded = false;

    @Nullable
    private ChessMove pendingPromotionMove;
    @Nullable
    private Vector2i promotionGuiOrigin;

    @UnknownNullability
    private GameHistoryWidget gameHistory;
    private int selectedMove;
    @UnknownNullability
    private Button resignButton;
    @UnknownNullability
    private Button drawOfferButton;
    @UnknownNullability
    private Button copyPgnButton;

    public ChessScreen(ChessGame game) {
        super(Component.translatable("chessGame.title", game.opponent.getProfile().name()));
        this.game = game;
        this.selectedMove = game.moveList.size() - 1;
    }

    @Override
    protected void init() {
        int startX = (this.width - BOARD_SIZE) / 2;
        int startY = (this.height - BOARD_SIZE) / 2;
        int buttonStartY = startY + BOARD_SIZE + BOARD_EDGE_PADDING;

        resignButton = Button.builder(
            Component.translatable("chessGame.resign"),
            button -> {
                if (TwoPlayerGame.CHESS_TYPE.getActiveGame(game.opponent.getProfile().id()) != game) {
                    return;
                }

                try {
                    ClientPacketListener connection = minecraft.getConnection();
                    assert connection != null;
                    GameProfile gameProfile = connection.getLocalGameProfile();
                    C2CPacketHandler.getInstance().sendPacket(new ChessResignC2CPacket(gameProfile.name(), gameProfile.id()), game.opponent);
                } catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                }
                TwoPlayerGame.CHESS_TYPE.removeActiveGame(game.opponent.getProfile().id());
                ClientCommandHelper.sendFeedback(Component.translatable("chessGame.youResigned"));
            }
        )
            .bounds(startX, buttonStartY, BOARD_SIZE / 2 - 2, 20)
            .build();
        resignButton.active = TwoPlayerGame.CHESS_TYPE.getActiveGame(game.opponent.getProfile().id()) == game;
        addRenderableWidget(resignButton);


        drawOfferButton = Button.builder(
            Component.empty(),
            button -> {
                ChessDrawOfferC2CPacket.Operation operation;
                if (game.drawOfferedBy == null) {
                    game.drawOfferedBy = game.yourColor;
                    operation = ChessDrawOfferC2CPacket.Operation.OFFER;
                } else if (game.drawOfferedBy == game.yourColor) {
                    game.drawOfferedBy = null;
                    operation = ChessDrawOfferC2CPacket.Operation.RETRACT;
                } else {
                    TwoPlayerGame.CHESS_TYPE.removeActiveGame(game.opponent.getProfile().id());
                    operation = ChessDrawOfferC2CPacket.Operation.ACCEPT;
                    ClientCommandHelper.sendFeedback(Component.translatable("chessGame.draw.agreement"));
                }

                try {
                    ClientPacketListener connection = minecraft.getConnection();
                    assert connection != null;
                    GameProfile gameProfile = connection.getLocalGameProfile();
                    C2CPacketHandler.getInstance().sendPacket(new ChessDrawOfferC2CPacket(gameProfile.name(), gameProfile.id(), operation), game.opponent);
                } catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                }
            }
        )
            .bounds(startX + BOARD_SIZE / 2 + 2, buttonStartY, BOARD_SIZE / 2 - 2, 20)
            .build();
        updateDrawOfferText();
        drawOfferButton.active = resignButton.active;
        addRenderableWidget(drawOfferButton);

        Button copyFenButton = Button.builder(
            Component.translatable("chessGame.copyFen"),
            button -> {
                ChessGame displayingGame = selectedMove + 1 == game.moveList.size() ? game : game.previousGameStates.get(selectedMove + 1);
                String fen = displayingGame.getFen();
                minecraft.keyboardHandler.setClipboard(fen);
                ClientCommandHelper.sendFeedback(Component.translatable("chessGame.copyFen.success", fen));
            }
        )
            .bounds(startX, buttonStartY + 24, BOARD_SIZE / 2 - 2, 20)
            .build();
        addRenderableWidget(copyFenButton);

        copyPgnButton = Button.builder(
            Component.translatable("chessGame.copyPgn"),
            button -> {
                String pgn = game.getPgn();
                minecraft.keyboardHandler.setClipboard(pgn);
                ClientCommandHelper.sendFeedback(Component.translatable("chessGame.copyPgn.success", pgn));
            }
        )
            .bounds(startX + BOARD_SIZE / 2 + 2, buttonStartY + 24, BOARD_SIZE / 2 - 2, 20)
            .build();
        copyPgnButton.active = !game.moveList.isEmpty();
        addRenderableWidget(copyPgnButton);

        int gameHistoryWidth = calcGameHistoryWidth();
        gameHistory = new GameHistoryWidget(minecraft, this.width - 5 - gameHistoryWidth, 10, gameHistoryWidth, this.height - 20, 20);
        gameHistory.refresh();
        addRenderableWidget(gameHistory);
    }

    private void updateDrawOfferText() {
        if (game.drawOfferedBy == null) {
            drawOfferButton.setMessage(Component.translatable("chessGame.offerDraw"));
        } else if (game.drawOfferedBy == game.yourColor) {
            drawOfferButton.setMessage(Component.translatable("chessGame.retractDrawOffer"));
        } else {
            drawOfferButton.setMessage(Component.translatable("chessGame.acceptDraw"));
        }
    }

    private int calcGameHistoryWidth() {
        return 2 * (maxWidthOf(Arrays.stream(ChessPieceType.VALUES).filter(piece -> piece != ChessPieceType.PAWN).map(piece -> piece.notation))
            + font.width("x")
            + 2 * (maxWidthOf(Stream.of('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')) + maxWidthOf(Stream.of('1', '2', '3', '4', '5', '6', '7', '8')))
            + maxWidthOf(Stream.of('+', '#'))
            + AbstractButton.TEXT_MARGIN * 4);
    }

    private int maxWidthOf(Stream<Character> chars) {
        return chars.mapToInt(c -> font.width(c.toString())).max().orElseThrow();
    }

    @Override
    public void tick() {
        if (!game.moveList.isEmpty() && lastSeenMove != null && !lastSeenMove.equals(game.moveList.getLast())) {
            playSound(getMoveSound(game.moveList.getLast()));
            gameHistory.refresh();
            moveAnimationTicks = 0;
            isCastlingAnimatingRookOnly = false;
        } else if (moveAnimationTicks != -1) {
            moveAnimationTicks++;
            if (moveAnimationTicks >= MOVE_ANIMATION_LENGTH) {
                moveAnimationTicks = -1;
                isCastlingAnimatingRookOnly = false;
            }
        }

        lastSeenMove = game.moveList.isEmpty() ? null : game.moveList.getLast();

        if (!wasGameEnded && TwoPlayerGame.CHESS_TYPE.getActiveGame(game.opponent.getProfile().id()) != game) {
            playSound(new ChessSound(GAME_END_SOUND, GAME_END_VOLUME, GAME_END_PITCH));
            resignButton.active = false;
            drawOfferButton.active = false;
            wasGameEnded = true;
        }

        updateDrawOfferText();
        copyPgnButton.active = !game.moveList.isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        ChessGame displayingGame = selectedMove + 1 == game.moveList.size() ? game : game.previousGameStates.get(selectedMove + 1);

        int startX = (this.width - BOARD_SIZE) / 2;
        int startY = (this.height - BOARD_SIZE) / 2;

        graphics.text(this.font, this.title, startX, startY - BOARD_EDGE_PADDING - font.lineHeight, 0xff_ffffff);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD_TEXTURE, startX, startY, 0, 0, BOARD_SIZE, BOARD_SIZE, BOARD_SIZE, BOARD_SIZE);

        for (int i = 0; i < 8; i++) {
            char rank = game.yourColor == ChessColor.WHITE ? (char) ('8' - i) : (char) ('1' + i);
            char file = game.yourColor == ChessColor.WHITE ? (char) ('a' + i) : (char) ('h' - i);

            graphics.centeredText(this.font, String.valueOf(file), startX + SQUARE_SIZE * i + SQUARE_SIZE / 2, startY - RANK_FILE_LABEL_PADDING - font.lineHeight, 0xff_ffffff);
            graphics.centeredText(this.font, String.valueOf(file), startX + SQUARE_SIZE * i + SQUARE_SIZE / 2, startY + BOARD_SIZE + RANK_FILE_LABEL_PADDING, 0xff_ffffff);
            graphics.text(this.font, String.valueOf(rank), startX - RANK_FILE_LABEL_PADDING - font.width(String.valueOf(rank)), startY + SQUARE_SIZE * i + SQUARE_SIZE / 2 - font.lineHeight / 2, 0xff_ffffff);
            graphics.text(this.font, String.valueOf(rank), startX + BOARD_SIZE + RANK_FILE_LABEL_PADDING, startY + SQUARE_SIZE * i + SQUARE_SIZE / 2 - font.lineHeight / 2, 0xff_ffffff);
        }

        Set<Vector2i> regularMoveSquares = new HashSet<>();
        Set<Vector2i> captureMoveSquares = new HashSet<>();
        if (selectedSquare != null && game.colorToMove == game.yourColor) {
            ChessPiece piece = game.getPiece(selectedSquare.x, selectedSquare.y);
            if (piece != null) {
                List<ChessMove> moves = new ArrayList<>();
                piece.type().generateMoves(game, selectedSquare.x, selectedSquare.y, moves);
                for (ChessMove move : moves) {
                    if (move.type().isLegalAccordingToCheck(game, move)) {
                        if (game.isCapture(move)) {
                            captureMoveSquares.add(move.to());
                        } else {
                            regularMoveSquares.add(move.to());
                        }
                    }
                }
            }
        }

        boolean isCheck = displayingGame.isCheck();

        // keep pieces on their initial squares pre-animation to stop them jumping to the end and then back to the start
        boolean isPreAnimation = moveAnimationTicks == -1 && lastSeenMove != null && !game.moveList.isEmpty() && !lastSeenMove.equals(game.moveList.getLast());
        ChessMove lastMove = selectedMove == -1 ? null : game.moveList.get(selectedMove);

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = displayingGame.getPiece(x, y);

                Vector2i squareStartPos = squareToStartScreenPos(x, y);
                int squareStartX = squareStartPos.x;
                int squareStartY = squareStartPos.y;

                int textureStartX = squareStartX + PADDING;
                int textureStartY = squareStartY + PADDING;

                if (selectedSquare != null && selectedSquare.x == x && selectedSquare.y == y) {
                    graphics.fill(squareStartX, squareStartY, squareStartX + SQUARE_SIZE, squareStartY + SQUARE_SIZE, SELECTED_HIGHLIGHT_COLOR);
                } else if (regularMoveSquares.contains(new Vector2i(x, y))) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, SQUARE_HIGHLIGHT_TEXTURE, textureStartX, textureStartY, 0, 0, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                } else if (captureMoveSquares.contains(new Vector2i(x, y))) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, CAPTURE_HIGHLIGHT_TEXTURE, textureStartX, textureStartY, 0, 0, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                } else if (squareHighlights.containsKey(new Vector2i(x, y))) {
                    graphics.fill(squareStartX, squareStartY, squareStartX + SQUARE_SIZE, squareStartY + SQUARE_SIZE, squareHighlights.get(new Vector2i(x, y)));
                } else if (lastMove != null && ((lastMove.from().x == x && lastMove.from().y == y) || (lastMove.to().x == x && lastMove.to().y == y))) {
                    graphics.fill(squareStartX, squareStartY, squareStartX + SQUARE_SIZE, squareStartY + SQUARE_SIZE, SELECTED_HIGHLIGHT_COLOR);
                }

                boolean renderPiece = true;
                if (dragging && selectedSquare != null && selectedSquare.x == x && selectedSquare.y == y) {
                    renderPiece = false;
                } else if ((isPreAnimation || moveAnimationTicks != -1) && !game.moveList.isEmpty()) {
                    ChessMove animatingMove = game.moveList.getLast();
                    if (!isCastlingAnimatingRookOnly && animatingMove.to().x == x && animatingMove.to().y == y) {
                        renderPiece = false;
                    } else if (animatingMove.type() == ChessMove.Type.CASTLE && animatingMove.to().y == y && Math.abs(animatingMove.to().x - x) == 1) {
                        renderPiece = false;
                    }
                }

                if (renderPiece && piece != null) {
                    if (isCheck && piece.type() == ChessPieceType.KING && piece.color() == displayingGame.colorToMove) {
                        graphics.blit(RenderPipelines.GUI_TEXTURED, CHECK_TEXTURE, textureStartX, textureStartY, 0, 0, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                    }

                    graphics.blit(RenderPipelines.GUI_TEXTURED, piece.texture, textureStartX, textureStartY, piece.u, piece.v, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                }
            }
        }

        if ((isPreAnimation || moveAnimationTicks != -1) && !game.moveList.isEmpty()) {
            ChessMove animatingMove = game.moveList.getLast();
            if (!isCastlingAnimatingRookOnly) {
                ChessPiece piece = game.getPiece(animatingMove.to().x, animatingMove.to().y);

                if (piece != null) {
                    if (animatingMove.type().isPromotion()) {
                        piece = piece.color() == ChessColor.WHITE ? ChessPiece.WHITE_PAWN : ChessPiece.BLACK_PAWN;
                    }

                    Vector2i fromPos = squareToStartScreenPos(animatingMove.from().x, animatingMove.from().y).add(PADDING, PADDING);
                    Vector2i toPos = squareToStartScreenPos(animatingMove.to().x, animatingMove.to().y).add(PADDING, PADDING);
                    Vector2i pos = interpolateMove(fromPos, toPos, (moveAnimationTicks + a) / MOVE_ANIMATION_LENGTH);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, piece.texture, pos.x, pos.y, piece.u, piece.v, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                }
            }

            if (animatingMove.type() == ChessMove.Type.CASTLE) {
                // animate the rook too
                int rookFromX, rookToX;
                if (animatingMove.to().x > 4) {
                    rookFromX = 7;
                    rookToX = 5;
                } else {
                    rookFromX = 0;
                    rookToX = 3;
                }

                ChessPiece piece = animatingMove.to().y == 0 ? ChessPiece.WHITE_ROOK : ChessPiece.BLACK_ROOK;
                Vector2i fromPos = squareToStartScreenPos(rookFromX, animatingMove.from().y).add(PADDING, PADDING);
                Vector2i toPos = squareToStartScreenPos(rookToX, animatingMove.to().y).add(PADDING, PADDING);
                Vector2i pos = interpolateMove(fromPos, toPos, (moveAnimationTicks + a) / MOVE_ANIMATION_LENGTH);
                graphics.blit(RenderPipelines.GUI_TEXTURED, piece.texture, pos.x, pos.y, piece.u, piece.v, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            }
        }

        if (dragging && selectedSquare != null) {
            ChessPiece piece = game.getPiece(selectedSquare.x, selectedSquare.y);
            if (piece != null) {
                int textureStartX = mouseX - PIECE_SIZE / 2;
                int textureStartY = mouseY - PIECE_SIZE / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, piece.texture, textureStartX, textureStartY, piece.u, piece.v, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            }
        }

        arrows.forEach((arrow, color) -> extractArrow(graphics, arrow, color));

        if (promotionGuiOrigin != null) {
            int guiOriginX = promotionGuiOrigin.x;
            int guiOriginY = promotionGuiOrigin.y;

            if (guiOriginX + 2 * SQUARE_SIZE > width) {
                guiOriginX -= 2 * SQUARE_SIZE;
            }
            if (guiOriginY + SQUARE_SIZE > height) {
                guiOriginY -= 2 * SQUARE_SIZE;
            }

            graphics.fill(guiOriginX, guiOriginY, guiOriginX + 2 * SQUARE_SIZE, guiOriginY + 2 * SQUARE_SIZE, CommonColors.WHITE);
            if (mouseX >= guiOriginX && mouseY >= guiOriginY && mouseX < guiOriginX + 2 * SQUARE_SIZE && mouseY < guiOriginY + 2 * SQUARE_SIZE) {
                int highlightX = (mouseX - guiOriginX) / SQUARE_SIZE * SQUARE_SIZE + guiOriginX;
                int highlightY = (mouseY - guiOriginY) / SQUARE_SIZE * SQUARE_SIZE + guiOriginY;
                graphics.fill(highlightX, highlightY, highlightX + SQUARE_SIZE, highlightY + SQUARE_SIZE, ARGB.opaque(SELECTED_HIGHLIGHT_COLOR));
            }

            for (int i = 0; i < 3; i++) {
                graphics.horizontalLine(guiOriginX, guiOriginX + 2 * SQUARE_SIZE, guiOriginY + i * SQUARE_SIZE, CommonColors.BLACK);
                graphics.verticalLine(guiOriginX + i * SQUARE_SIZE, guiOriginY, guiOriginY + 2 * SQUARE_SIZE, CommonColors.BLACK);
            }

            ChessPiece[] promotionPieces = game.yourColor == ChessColor.WHITE ? WHITE_PROMOTION_PIECES : BLACK_PROMOTION_PIECES;
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    ChessPiece promotionPiece = promotionPieces[x + y * 2];
                    graphics.blit(RenderPipelines.GUI_TEXTURED, promotionPiece.texture, guiOriginX + x * SQUARE_SIZE + PADDING, guiOriginY + y * SQUARE_SIZE + PADDING, promotionPiece.u, promotionPiece.v, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
                }
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_LEFT -> selectMove(Math.max(-1, selectedMove - 1));
            case InputConstants.KEY_RIGHT -> selectMove(Math.min(game.moveList.size() - 1, selectedMove + 1));
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (promotionGuiOrigin != null) {
            int guiOriginX = promotionGuiOrigin.x;
            int guiOriginY = promotionGuiOrigin.y;

            if (guiOriginX + 2 * SQUARE_SIZE > width) {
                guiOriginX -= 2 * SQUARE_SIZE;
            }
            if (guiOriginY + SQUARE_SIZE > height) {
                guiOriginY -= 2 * SQUARE_SIZE;
            }

            if (pendingPromotionMove != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= guiOriginX && event.y() >= guiOriginY && event.x() < guiOriginX + 2 * SQUARE_SIZE && event.y() < guiOriginY + 2 * SQUARE_SIZE) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                int x = ((int) event.x() - guiOriginX) / SQUARE_SIZE;
                int y = ((int) event.y() - guiOriginY) / SQUARE_SIZE;
                doMove(new ChessMove(pendingPromotionMove.from(), pendingPromotionMove.to(), PROMOTION_MOVE_TYPES[x + y * 2]), true);
                promotionGuiOrigin = null;
                pendingPromotionMove = null;
                return true;
            }

            promotionGuiOrigin = null;
            pendingPromotionMove = null;
        }

        Vector2i square = screenPosToSquare((int) event.x(), (int) event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && selectedMove + 1 == game.moveList.size()) {
            isSecondClick = false;
            if (square == null) {
                selectedSquare = null;
            } else {
                ChessPiece piece = game.getPiece(square.x, square.y);
                if (piece != null && piece.color() == game.yourColor) {
                    isSecondClick = square.equals(selectedSquare);
                    selectedSquare = square;
                    dragging = true;
                } else if (selectedSquare != null) {
                    tryDoMove(square, (int) event.x(), (int) event.y(), true);
                }
            }
        } else {
            selectedSquare = null;
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            highlightStartSquare = square;
        } else {
            squareHighlights.clear();
            arrows.clear();
            highlightStartSquare = null;
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Vector2i square = screenPosToSquare((int) event.x(), (int) event.y());

        switch (event.button()) {
            case InputConstants.MOUSE_BUTTON_LEFT -> {
                if (dragging) {
                    dragging = false;
                    if (square != null) {
                        if (!square.equals(selectedSquare)) {
                            tryDoMove(square, (int) event.x(), (int) event.y(), false);
                        } else if (isSecondClick) {
                            selectedSquare = null;
                        }
                    }
                }
            }
            case InputConstants.MOUSE_BUTTON_RIGHT -> {
                if (highlightStartSquare != null) {
                    if (square != null) {
                        boolean isArrow = !square.equals(highlightStartSquare);

                        int color;
                        if (event.hasAltDown()) {
                            color = BLUE_HIGHLIGHT;
                        } else if (event.hasControlDown()) {
                            color = isArrow ? RED_HIGHLIGHT : ORANGE_HIGHLIGHT;
                        } else if (event.hasShiftDown()) {
                            color = GREEN_HIGHLIGHT;
                        } else {
                            color = isArrow ? ORANGE_HIGHLIGHT : RED_HIGHLIGHT;
                        }

                        if (isArrow) {
                            arrows.merge(new Arrow(highlightStartSquare, square), color, (oldCol, col) -> col.equals(oldCol) ? null : col);
                        } else {
                            squareHighlights.merge(square, color, (oldCol, col) -> col.equals(oldCol) ? null : col);
                        }
                    }
                    highlightStartSquare = null;
                }
            }
        }

        return super.mouseReleased(event);
    }

    private void tryDoMove(Vector2i square, int mouseX, int mouseY, boolean animate) {
        if (selectedSquare == null) {
            return;
        }

        if (game.colorToMove == game.yourColor && TwoPlayerGame.CHESS_TYPE.getActiveGame(game.opponent.getProfile().id()) == game) {
            ChessPiece piece = game.getPiece(selectedSquare.x, selectedSquare.y);

            List<ChessMove> moves = new ArrayList<>();
            if (piece != null) {
                piece.type().generateMoves(game, selectedSquare.x, selectedSquare.y, moves);
            }

            ChessMove legalMove = null;
            for (ChessMove move : moves) {
                if (move.to().equals(square) && move.type().isLegalAccordingToCheck(game, move)) {
                    legalMove = move;
                    break;
                }
            }

            if (legalMove != null) {
                if (legalMove.type().isPromotion()) {
                    promotionGuiOrigin = new Vector2i(mouseX, mouseY);
                    pendingPromotionMove = legalMove;
                } else {
                    doMove(legalMove, animate);
                }
            }
        }

        selectedSquare = null;
    }

    private void doMove(ChessMove legalMove, boolean animate) {
        String moveNotation = game.getAlgebraicNotation(legalMove);
        game.makeMove(legalMove);
        lastSeenMove = legalMove;

        playSound(getMoveSound(legalMove));
        gameHistory.refresh();

        if (animate) {
            moveAnimationTicks = 0;
            isCastlingAnimatingRookOnly = false;
        } else if (legalMove.type() == ChessMove.Type.CASTLE) {
            moveAnimationTicks = 0;
            isCastlingAnimatingRookOnly = true;
        } else {
            moveAnimationTicks = -1;
            isCastlingAnimatingRookOnly = false;
        }

        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            assert connection != null;
            GameProfile gameProfile = connection.getLocalGameProfile();
            C2CPacketHandler.getInstance().sendPacket(new ChessMoveC2CPacket(gameProfile.name(), gameProfile.id(), legalMove), game.opponent);
        } catch (CommandSyntaxException e) {
            ClientCommandHelper.sendError(Component.translationArg(e.getRawMessage()));
        }

        ClientCommandHelper.sendFeedback(Component.translatable("chessGame.youMoved", moveNotation));

        Component endCondition = game.detectEndCondition();
        if (endCondition != null) {
            ClientCommandHelper.sendFeedback(endCondition);
            TwoPlayerGame.CHESS_TYPE.removeActiveGame(game.opponent.getProfile().id());
        }
    }

    private ChessSound getMoveSound(ChessMove move) {
        if (game.isCheck()) {
            return new ChessSound(CHECK_SOUND, CHECK_VOLUME, CHECK_PITCH);
        } else if (move.type() == ChessMove.Type.CASTLE) {
            return new ChessSound(CASTLING_SOUND, CASTLING_VOLUME, CASTLING_PITCH);
        } else if (move.type() == ChessMove.Type.EN_PASSANT || game.getPieceInPreviousPosition(move.to().x, move.to().y) != null) {
            return new ChessSound(CAPTURE_SOUND, CAPTURE_VOLUME, CAPTURE_PITCH);
        } else {
            return new ChessSound(MOVE_SOUND, MOVE_VOLUME, MOVE_PITCH);
        }
    }

    private static void playSound(ChessSound sound) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.sound.value().location(), SoundSource.UI, sound.volume, sound.pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    private void selectMove(int moveNumber) {
        selectedMove = moveNumber;
        dragging = false;
        selectedSquare = null;
        moveAnimationTicks = -1;
        isCastlingAnimatingRookOnly = false;
    }

    @Nullable
    private Vector2i screenPosToSquare(int x, int y) {
        int startX = (this.width - BOARD_SIZE) / 2;
        int startY = (this.height - BOARD_SIZE) / 2;
        if (x < startX || x >= startX + BOARD_SIZE || y < startY || y >= startY + BOARD_SIZE) {
            return null;
        }

        if (game.yourColor == ChessColor.WHITE) {
            return new Vector2i((x - startX) / SQUARE_SIZE, 7 - (y - startY) / SQUARE_SIZE);
        } else {
            return new Vector2i(7 - (x - startX) / SQUARE_SIZE, (y - startY) / SQUARE_SIZE);
        }
    }

    private Vector2i squareToStartScreenPos(int x, int y) {
        int startX = (this.width - BOARD_SIZE) / 2;
        int startY = (this.height - BOARD_SIZE) / 2;
        if (game.yourColor == ChessColor.WHITE) {
            return new Vector2i(startX + SQUARE_SIZE * x, startY + SQUARE_SIZE * (7 - y));
        } else {
            return new Vector2i(startX + SQUARE_SIZE * (7 - x), startY + SQUARE_SIZE * y);
        }
    }

    private void extractArrow(GuiGraphicsExtractor graphics, Arrow arrow, int color) {
        Vector2i fromPos = squareToStartScreenPos(arrow.from.x, arrow.from.y);
        Vector2i toPos = squareToStartScreenPos(arrow.to.x, arrow.to.y);
        if (Math.abs(arrow.to.x - arrow.from.x) + Math.abs(arrow.to.y - arrow.from.y) == 3 && arrow.from.x != arrow.to.x && arrow.from.y != arrow.to.y) {
            // special case for knight move arrows
            graphics.pose().pushMatrix();
            graphics.pose().translate(fromPos.x + (float) SQUARE_SIZE / 2, fromPos.y + (float) SQUARE_SIZE / 2);

            if (Math.abs(arrow.to.y - arrow.from.y) == 2) {
                if (toPos.y < fromPos.y) {
                    graphics.pose().rotate(-Mth.HALF_PI);
                } else {
                    graphics.pose().rotate(Mth.HALF_PI);
                }
            } else {
                if (toPos.x < fromPos.x) {
                    graphics.pose().rotate(Mth.PI);
                }
            }

            graphics.fill(SQUARE_SIZE / 4, -ARROW_HALF_THICKNESS, SQUARE_SIZE * 2 + ARROW_HALF_THICKNESS, ARROW_HALF_THICKNESS, color);

            int dx = arrow.to.x - arrow.from.x;
            int dy = arrow.to.y - arrow.from.y;
            if ((dx == 2 && dy == 1) || (dx == 1 && dy == -2) || (dx == -2 && dy == -1) || (dx == -1 && dy == 2)) {
                graphics.fill(SQUARE_SIZE * 2 - ARROW_HALF_THICKNESS, -SQUARE_SIZE * 3 / 4, SQUARE_SIZE * 2 + ARROW_HALF_THICKNESS, -ARROW_HALF_THICKNESS, color);
                extractTriangle(graphics, SQUARE_SIZE * 2, -SQUARE_SIZE * 3 / 4 - ARROW_HEAD_SIZE, SQUARE_SIZE * 2 - ARROW_HEAD_SIZE, -SQUARE_SIZE * 3 / 4, SQUARE_SIZE * 2 + ARROW_HEAD_SIZE, -SQUARE_SIZE * 3 / 4, color);
            } else {
                graphics.fill(SQUARE_SIZE * 2 - ARROW_HALF_THICKNESS, ARROW_HALF_THICKNESS, SQUARE_SIZE * 2 + ARROW_HALF_THICKNESS, SQUARE_SIZE * 3 / 4, color);
                extractTriangle(graphics, SQUARE_SIZE * 2 - ARROW_HEAD_SIZE, SQUARE_SIZE * 3 / 4, SQUARE_SIZE * 2, SQUARE_SIZE * 3 / 4 + ARROW_HEAD_SIZE, SQUARE_SIZE * 2 + ARROW_HEAD_SIZE, SQUARE_SIZE * 3 / 4, color);
            }
            graphics.pose().popMatrix();
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(fromPos.x + (float) SQUARE_SIZE / 2, fromPos.y + (float) SQUARE_SIZE / 2);
            graphics.pose().rotate((float) Math.atan2(toPos.y - fromPos.y, toPos.x - fromPos.x));
            int arrowLength = (int) Math.hypot(toPos.x - fromPos.x, toPos.y - fromPos.y) - SQUARE_SIZE / 4;
            graphics.fill(SQUARE_SIZE / 4, -ARROW_HALF_THICKNESS, arrowLength, ARROW_HALF_THICKNESS, color);
            extractTriangle(graphics, arrowLength, -ARROW_HEAD_SIZE, arrowLength, ARROW_HEAD_SIZE, arrowLength + ARROW_HEAD_SIZE, 0, color);
            graphics.pose().popMatrix();
        }
    }

    private static void extractTriangle(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        graphics.guiRenderState.addGuiElement(new ColoredTriangleRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, x2, y2, color, graphics.scissorStack.peek()));
    }

    private static Vector2i interpolateMove(Vector2i from, Vector2i to, float t) {
        return new Vector2i((int) Mth.lerp(sigmoid(t), from.x, to.x), (int) Mth.lerp(sigmoid(t), from.y, to.y));
    }

    private static float sigmoid(float t) {
        // https://www.desmos.com/calculator/3zhzwbfrxd
        class Constants {
            static final float P = 0.5f;
            static final float S = 0.2f;
            static final float C = (2 / (1 - S)) - 1;
            static final float P_POW_C_MINUS_ONE = (float) Math.pow(P, C - 1);
            static final float ONE_MINUS_P_POW_C_MINUS_ONE = (float) Math.pow(1 - P, C - 1);
        }

        t = Mth.clamp(t, 0, 1);

        if (t <= Constants.P) {
            return (float) Math.pow(t, Constants.C) / Constants.P_POW_C_MINUS_ONE;
        } else {
            return 1 - (float) Math.pow(1 - t, Constants.C) / Constants.ONE_MINUS_P_POW_C_MINUS_ONE;
        }
    }

    private record Arrow(Vector2i from, Vector2i to) {
    }

    private record ChessSound(Holder<SoundEvent> sound, float volume, float pitch) {
        ChessSound(SoundEvent sound, float volume, float pitch) {
            this(Holder.direct(sound), volume, pitch);
        }
    }

    private final class GameHistoryWidget extends ContainerObjectSelectionList<GameHistoryWidget.Entry> {
        public GameHistoryWidget(Minecraft minecraft, int x, int y, int width, int height, int entryHeight) {
            super(minecraft, width, height, y, entryHeight);
            setX(x);
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        }

        @Override
        public int getRowLeft() {
            return getX();
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_WIDTH;
        }

        @Override
        protected int scrollBarX() {
            return getRowRight();
        }

        void refresh() {
            List<Entry> newEntries = new ArrayList<>(game.algebraicMoveList.size() / 2);

            for (int i = 0; i < game.moveList.size(); i += 2) {
                AbstractButton whiteMove = makeButton(game.algebraicMoveList.get(i), i);
                AbstractButton blackMove = i + 1 < game.algebraicMoveList.size() ? makeButton(game.algebraicMoveList.get(i + 1), i + 1) : null;
                newEntries.add(new Entry(whiteMove, blackMove));
            }

            replaceEntries(newEntries);

            setScrollAmount(maxScrollAmount());
            selectMove(game.moveList.size() - 1);
        }

        private AbstractButton makeButton(String move, int moveNumber) {
            return new MoveButton(
                moveNumber,
                (getRowWidth() - Entry.CONTENT_PADDING * 2) / 2,
                20,
                Component.literal(move),
                button -> selectMove(moveNumber)
            );
        }

        private static final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            private final AbstractButton whiteMove;
            @Nullable
            private final AbstractButton blackMove;

            Entry(AbstractButton whiteMove, @Nullable AbstractButton blackMove) {
                this.whiteMove = whiteMove;
                this.blackMove = blackMove;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                if (blackMove == null) {
                    return List.of(whiteMove);
                } else {
                    return List.of(whiteMove, blackMove);
                }
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                whiteMove.setX(getContentX());
                whiteMove.setY(getContentY());
                whiteMove.extractRenderState(graphics, mouseX, mouseY, a);

                if (blackMove != null) {
                    blackMove.setX(getContentX() + getContentWidth() / 2);
                    blackMove.setY(getContentY());
                    blackMove.extractRenderState(graphics, mouseX, mouseY, a);
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                if (blackMove == null) {
                    return List.of(whiteMove);
                } else {
                    return List.of(whiteMove, blackMove);
                }
            }
        }

        private final class MoveButton extends Button {
            private final int moveNumber;

            MoveButton(int moveNumber, int width, int height, Component message, OnPress onPress) {
                super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
                this.moveNumber = moveNumber;
            }

            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
                if (selectedMove == moveNumber) {
                    graphics.horizontalLine(getX(), getRight() - 1, getY(), CommonColors.WHITE);
                    graphics.horizontalLine(getX(), getRight() - 1, getBottom() - 1, CommonColors.WHITE);
                    graphics.verticalLine(getX(), getY(), getBottom() - 1, CommonColors.WHITE);
                    graphics.verticalLine(getRight() - 1, getY(), getBottom() - 1, CommonColors.WHITE);
                }
            }
        }
    }
}
