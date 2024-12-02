package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutConnectFourPieceC2CPacket;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

public class ConnectFourCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.createCommandTree());
    }

    public static void onPutConnectFourPieceC2CPacket(PutConnectFourPieceC2CPacket packet) {
        UUID senderUUID = packet.senderUUID();
        ConnectFourGame game = TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.getActiveGame(senderUUID);
        if (game == null) {
            return;
        }
        game.onMove(packet.x(), game.opponentPiece());
    }

    public static class ConnectFourGame {
        public static final int WIDTH = 7;
        public static final int HEIGHT = 6;

        public final PlayerInfo opponent;
        public final Piece yourPiece;
        public Piece activePiece;
        public final Piece[][] board;
        @Nullable
        public Winner winner;

        public ConnectFourGame(PlayerInfo opponent, Piece yourPiece) {
            this.opponent = opponent;
            this.yourPiece = yourPiece;
            this.activePiece = Piece.RED;
            this.board = new Piece[WIDTH][HEIGHT];
            this.winner = null;
        }

        public void onMove(int x, Piece piece) {
            final Minecraft mc = Minecraft.getInstance();
            final ClientPacketListener connection = mc.getConnection();
            assert connection != null;
            if (piece != activePiece) {
                LOGGER.warn("Invalid piece, the active piece is {} and the piece that was attempted to be placed was {}", this.activePiece.translate(), piece.translate());
                return;
            }

            if (!this.isGameActive()) {
                LOGGER.warn("Tried to add piece to the already completed game with {}.", this.opponent.getProfile().getName());
                return;
            }

            if (!this.addPiece(x, piece)) {
                LOGGER.warn("Failed to add piece to your Connect Four game with {}.", this.opponent.getProfile().getName());
                return;
            }

            if (this.isYourTurn()) {
                try {
                    PutConnectFourPieceC2CPacket packet = new PutConnectFourPieceC2CPacket(connection.getLocalGameProfile().getName(), connection.getLocalGameProfile().getId(), x);
                    C2CPacketHandler.getInstance().sendPacket(packet, this.opponent);
                } catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                }
            }

            String sender = this.opponent.getProfile().getName();
            UUID senderUUID = this.opponent.getProfile().getId();
            this.activePiece = piece.opposite();
            if ((this.winner = this.getWinner()) != null) {
                if (this.winner == this.yourPiece.asWinner()) {
                    TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.onWon(sender, senderUUID);
                } else if (this.winner == this.yourPiece.opposite().asWinner()) {
                    TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.onLost(sender, senderUUID);
                } else if (this.winner == Winner.DRAW) {
                    TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.onDraw(sender, senderUUID);
                }
            } else {
                if (this.isYourTurn()) {
                    TwoPlayerGame.CONNECT_FOUR_GAME_TYPE.onMove(sender);
                }
            }
        }

        public boolean isYourTurn() {
            return this.activePiece == this.yourPiece;
        }

        public boolean isGameActive() {
            return this.winner == null;
        }

        public boolean canMove() {
            return this.isYourTurn() && this.isGameActive();
        }

        public Piece opponentPiece() {
            return yourPiece.opposite();
        }

        public boolean addPiece(int x, Piece piece) {
            int y;
            if (isValidRow(x) && (y = this.getPlacementY(x)) < HEIGHT) {
                this.board[x][y] = piece;
                return true;
            }
            return false;
        }

        @Nullable
        private Winner getWinner() {
            // check horizontally
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (this.board[x][y] != null && this.board[x][y] == this.board[x + 1][y] && this.board[x][y] == this.board[x + 2][y] && this.board[x][y] == this.board[x + 3][y]) {
                        return this.board[x][y].asWinner();
                    }
                }
            }

            // check vertically
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if (this.board[x][y] != null && this.board[x][y] == this.board[x][y + 1] && this.board[x][y] == this.board[x][y + 2] && this.board[x][y] == this.board[x][y + 3]) {
                        return this.board[x][y].asWinner();
                    }
                }
            }

            // check horizontally (northeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if (this.board[x][y] != null && this.board[x][y] == this.board[x + 1][y + 1] && this.board[x][y] == this.board[x + 2][y + 2] && this.board[x][y] == this.board[x + 3][y + 3]) {
                        return this.board[x][y].asWinner();
                    }
                }
            }

            // check horizontally (southeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 3; y < HEIGHT; y++) {
                    if (this.board[x][y] != null && this.board[x][y] == this.board[x + 1][y - 1] && this.board[x][y] == this.board[x + 2][y - 2] && this.board[x][y] == this.board[x + 3][y - 3]) {
                        return this.board[x][y].asWinner();
                    }
                }
            }

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (this.board[x][y] == null) {
                        // still a space to play
                        return null;
                    }
                }
            }

            // no spaces left, game ends in a draw
            return Winner.DRAW;
        }

        public static boolean isValidRow(int x) {
            return 0 <= x && x < WIDTH;
        }

        public int getPlacementY(int x) {
            int y = 0;
            for (Piece piece : this.board[x]) {
                if (piece == null) {
                    break;
                }
                y++;
            }

            return y;
        }
    }

    public enum Piece {
        RED,
        YELLOW;

        public Piece opposite() {
            return switch (this) {
                case RED -> YELLOW;
                case YELLOW -> RED;
            };
        }

        public Component translate() {
            return switch (this) {
                case RED -> Component.translatable("connectFourGame.pieceRed");
                case YELLOW -> Component.translatable("connectFourGame.pieceYellow");
            };
        }

        public Winner asWinner() {
            return switch (this) {
                case RED -> Winner.RED;
                case YELLOW -> Winner.YELLOW;
            };
        }

        public void render(GuiGraphics graphics, int x, int y, boolean transparent) {
            int xOffset = switch (this) {
                case RED -> 0;
                case YELLOW -> 16;
            };
            graphics.blit(
                RenderType::guiTextured,
                ConnectFourGameScreen.PIECES_TEXTURE,
                x,
                y,
                xOffset,
                0,
                ConnectFourGameScreen.PIECE_WIDTH,
                ConnectFourGameScreen.PIECE_HEIGHT,
                ConnectFourGameScreen.TEXTURE_PIECE_WIDTH,
                ConnectFourGameScreen.TEXTURE_PIECE_HEIGHT,
                ConnectFourGameScreen.TEXTURE_PIECES_WIDTH,
                ConnectFourGameScreen.TEXTURE_PIECES_HEIGHT,
                transparent ? 0X7F_FFFFFF : 0XFF_FFFFFF
            );
        }
    }

    public enum Winner {
        RED,
        YELLOW,
        DRAW
    }

    public static class ConnectFourGameScreen extends Screen {
        private final ConnectFourGame game;

        private static final ResourceLocation BOARD_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/connect_four/board.png");
        private static final ResourceLocation PIECES_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/connect_four/pieces.png");

        private static final int SCALE = 4;

        private static final int TEXTURE_PIECE_WIDTH = 16;
        private static final int TEXTURE_PIECE_HEIGHT = 16;
        private static final int TEXTURE_BOARD_BORDER_WIDTH = 1;
        private static final int TEXTURE_BOARD_BORDER_HEIGHT = 1;
        private static final int TEXTURE_SLOT_BORDER_WIDTH = 1;
        private static final int TEXTURE_SLOT_BORDER_HEIGHT = 1;
        private static final int TEXTURE_SLOT_WIDTH = TEXTURE_PIECE_WIDTH + 2 * TEXTURE_SLOT_BORDER_WIDTH;
        private static final int TEXTURE_SLOT_HEIGHT = TEXTURE_PIECE_HEIGHT + 2 * TEXTURE_SLOT_BORDER_HEIGHT;
        private static final int TEXTURE_BOARD_WIDTH = TEXTURE_SLOT_WIDTH * ConnectFourGame.WIDTH + TEXTURE_BOARD_BORDER_WIDTH * 2;
        private static final int TEXTURE_BOARD_HEIGHT = TEXTURE_SLOT_HEIGHT * ConnectFourGame.HEIGHT + TEXTURE_BOARD_BORDER_HEIGHT * 2;
        private static final int TEXTURE_PIECES_WIDTH = 2 * TEXTURE_PIECE_WIDTH; // red and yellow
        private static final int TEXTURE_PIECES_HEIGHT = TEXTURE_PIECE_HEIGHT;
        
        private static final int BOARD_WIDTH = SCALE * TEXTURE_BOARD_WIDTH;
        private static final int BOARD_HEIGHT = SCALE * TEXTURE_BOARD_HEIGHT;
        private static final int PIECE_WIDTH = SCALE * TEXTURE_PIECE_WIDTH;
        private static final int PIECE_HEIGHT = SCALE * TEXTURE_PIECE_HEIGHT;
        private static final int BOARD_BORDER_WIDTH = SCALE * TEXTURE_BOARD_BORDER_WIDTH;
        private static final int BOARD_BORDER_HEIGHT = SCALE * TEXTURE_BOARD_BORDER_HEIGHT;
        private static final int SLOT_BORDER_WIDTH = SCALE * TEXTURE_SLOT_BORDER_WIDTH;
        private static final int SLOT_BORDER_HEIGHT = SCALE * TEXTURE_SLOT_BORDER_HEIGHT;
        private static final int SLOT_WIDTH = SCALE * TEXTURE_SLOT_WIDTH;
        private static final int SLOT_HEIGHT = SCALE * TEXTURE_SLOT_HEIGHT;
        
        public ConnectFourGameScreen(ConnectFourGame game) {
            super(Component.translatable("connectFourGame.title", game.opponent.getProfile().getName()));
            this.game = game;
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            int startX = (this.width - BOARD_WIDTH) / 2;
            int startY = (this.height - BOARD_HEIGHT) / 2;
            Component gameStateTranslate = getGameStateTranslate();

            graphics.drawString(this.font, Component.translatable("connectFourGame.pieceSet", this.game.yourPiece.translate()), startX, startY - 20, 0xff_ffffff);
            graphics.drawString(this.font, this.title, startX, startY - 10, 0xff_ffffff);
            graphics.drawString(this.font, gameStateTranslate, startX + BOARD_WIDTH - this.font.width(gameStateTranslate), startY - 10, 0xff_ffffff);

            graphics.blit(
                RenderType::guiTextured,
                BOARD_TEXTURE,
                startX,
                startY,
                0,
                0,
                BOARD_WIDTH,
                BOARD_HEIGHT,
                TEXTURE_BOARD_WIDTH,
                TEXTURE_BOARD_HEIGHT,
                TEXTURE_BOARD_WIDTH,
                TEXTURE_BOARD_HEIGHT
            );

            for (int x = 0; x < ConnectFourGame.WIDTH; x++) {
                for (int y = 0; y < ConnectFourGame.HEIGHT; y++) {
                    Piece piece = this.game.board[x][y];
                    if (piece != null) {
                        piece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (ConnectFourGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, false);
                    }
                }
            }

            int boardMinX = startX + BOARD_BORDER_WIDTH;
            int boardMaxX = startX + BOARD_WIDTH - BOARD_BORDER_WIDTH * 2;
            int boardMaxY = startY + BOARD_HEIGHT;
            if (this.game.canMove() && boardMinX <= mouseX && mouseX < boardMaxX && mouseY < boardMaxY) {
                int x = (mouseX - boardMinX) / SLOT_WIDTH;
                int y = this.game.getPlacementY(x);
                if (y < ConnectFourGame.HEIGHT) {
                    game.yourPiece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (ConnectFourGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, true);
                }
            }
        }

        private Component getGameStateTranslate() {
            if (game.isGameActive()) {
                if (this.game.isYourTurn()) {
                    return Component.translatable("connectFourGame.yourMove");
                } else {
                    return Component.translatable("connectFourGame.opponentMove");
                }
            } else {
                if (game.winner == Winner.DRAW) {
                    return Component.translatable("connectFourGame.draw");
                } else if (game.winner == game.yourPiece.asWinner()) {
                    return Component.translatable("connectFourGame.won").withStyle(ChatFormatting.GREEN);
                } else {
                    return Component.translatable("connectFourGame.lost").withStyle(ChatFormatting.RED);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = (this.width - BOARD_WIDTH) / 2;
            int startY = (this.height - BOARD_HEIGHT) / 2;

            int boardMinX = startX + BOARD_BORDER_WIDTH;
            int boardMaxX = startX + BOARD_WIDTH - BOARD_BORDER_WIDTH * 2;
            int boardMaxY = startY + BOARD_HEIGHT;

            if (!(boardMinX <= mouseX && mouseX < boardMaxX && mouseY < boardMaxY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            
            int x = (int) ((mouseX - boardMinX) / SLOT_WIDTH);
            if (this.game.canMove()) {
                this.game.onMove(x, game.yourPiece);
                return true;
            }

            return false;
        }
    }
}
