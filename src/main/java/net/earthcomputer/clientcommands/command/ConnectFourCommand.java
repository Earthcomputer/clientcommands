package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutConnectFourPieceC2CPacket;
import net.earthcomputer.clientcommands.features.TwoPlayerGameType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class ConnectFourCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.createCommandTree());
    }

    public static void onPutConnectFourPieceC2CPacket(PutConnectFourPieceC2CPacket packet) {
        String sender = packet.sender();
        ConnectFourGame game = TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGame(sender);
        if (game == null) {
            return;
        }
        game.onMove(packet.x(), game.opponentPiece());
    }

    public static class ConnectFourGame {
        public static final int WIDTH = 7;
        public static final int HEIGHT = 6;

        public final PlayerInfo opponent;
        public final byte yourPiece;
        public byte activePiece;
        public final byte[][] board;
        /**
         * 0 for undecided
         * 1 for red
         * 2 for yellow
         * 3 for draw
        */
        public byte winner;

        public ConnectFourGame(PlayerInfo opponent, byte yourPiece) {
            this.opponent = opponent;
            this.yourPiece = yourPiece;
            this.activePiece = Piece.RED;
            this.board = new byte[WIDTH][HEIGHT];
            this.winner = 0;
        }

        public void onMove(int x, byte piece) {
            if (piece != activePiece) {
                LOGGER.warn("Invalid piece, the active piece is {} and the piece that was attempted to be placed was {}", Piece.name(activePiece), Piece.name(piece));
                return;
            }

            if (!this.isGameActive()) {
                LOGGER.warn("Tried to add piece to the already completed game with {}.", this.opponent.getProfile().getName());
                return;
            }

            if (!this.addPiece(x, piece)) {
                LOGGER.warn("Failed to add piece to your Four in a Row game with {}.", this.opponent.getProfile().getName());
                return;
            }

            if (this.isYourTurn()) {
                try {
                    PutConnectFourPieceC2CPacket packet = new PutConnectFourPieceC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), x);
                    C2CPacketHandler.getInstance().sendPacket(packet, this.opponent);
                } catch (CommandSyntaxException e) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                }
            }

            String sender = this.opponent.getProfile().getName();
            this.activePiece = Piece.opposite(piece);
            if ((this.winner = this.getWinner()) != 0) {
                if (this.winner == this.yourPiece) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("fourInARowGame.won", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                } else if (this.winner == Piece.opposite(this.yourPiece)) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.putConnectFourPieceC2CPacket.incoming.lost", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                } else if (this.winner == 3) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("fourInARowGame.draw", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                }
            } else {
                if (this.isYourTurn()) {
                    MutableComponent component = Component.translatable("c2cpacket.putConnectFourPieceC2CPacket.incoming", sender);
                    component.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cconnectfour open " + sender))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/cconnectfour open " + sender))));
                    Minecraft.getInstance().gui.getChat().addMessage(component);
                }
            }
        }

        public boolean isYourTurn() {
            return this.activePiece == this.yourPiece;
        }

        public boolean isGameActive() {
            return this.winner == 0;
        }

        public boolean canMove() {
            return this.isYourTurn() && this.isGameActive();
        }

        public byte opponentPiece() {
            if (yourPiece == Piece.RED) {
                return Piece.YELLOW;
            } else {
                return Piece.RED;
            }
        }

        public boolean addPiece(int x, byte piece) {
            int y;
            if (isValidRow(x) && (y = this.getPlacementY(x)) < HEIGHT) {
                // this only exists to signify that each bit represents a different piece
                this.board[x][y] |= piece;
                return true;
            }
            return false;
        }

        // The `&` bitmasks only work because each piece has a unique bit, therefore making each bit represent if that piece is in that position, doing an & on all of them tells you which pieces are represented in every space.
        private byte getWinner() {
            // check horizontally
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if ((this.board[x][y] & this.board[x + 1][y] & this.board[x + 2][y] & this.board[x + 3][y]) > 0) {
                        return this.board[x][y];
                    }
                }
            }

            // check vertically
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if ((this.board[x][y] & this.board[x][y + 1] & this.board[x][y + 2] & this.board[x][y + 3]) > 0) {
                        return this.board[x][y];
                    }
                }
            }

            // check horizontally (northeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if ((this.board[x][y] & this.board[x + 1][y + 1] & this.board[x + 2][y + 2] & this.board[x + 3][y + 3]) > 0) {
                        return this.board[x][y];
                    }
                }
            }

            // check horizontally (southeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 3; y < HEIGHT; y++) {
                    if ((this.board[x][y] & this.board[x + 1][y - 1] & this.board[x + 2][y - 2] & this.board[x + 3][y - 3]) > 0) {
                        return this.board[x][y];
                    }
                }
            }

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (this.board[x][y] == 0) {
                        // still a space to play
                        return 0;
                    }
                }
            }

            // no spaces left, game ends in a draw
            return Piece.RED | Piece.YELLOW;
        }

        public static boolean isValidRow(int x) {
            return 0 <= x && x < WIDTH;
        }

        public int getPlacementY(int x) {
            int y = 0;
            for (byte piece : this.board[x]) {
                if (piece == 0) {
                    break;
                }
                y++;
            }

            return y;
        }
    }

    public static class Piece {
        public static final byte RED = 1 << 0;
        public static final byte YELLOW = 1 << 1;

        public static byte opposite(byte piece) {
            return switch (piece) {
                case RED -> YELLOW;
                case YELLOW -> RED;
                default -> throw new IllegalStateException("Unexpected value: " + piece);
            };
        }

        public static Component name(byte piece) {
            return switch (piece) {
                case RED -> Component.translatable("fourInARowGame.pieceRed");
                case YELLOW -> Component.translatable("fourInARowGame.pieceYellow");
                default -> throw new IllegalStateException("Unexpected value: " + piece);
            };
        }

        public static void render(GuiGraphics graphics, int x, int y, byte piece, boolean transparent) {
            if (piece == 0) {
                return;
            }
            int xOffset = switch (piece) {
                case RED -> 0;
                case YELLOW -> 16;
                default -> throw new IllegalStateException("Unexpected value: " + piece);
            };
            graphics.innerBlit(
                ConnectFourGameScreen.PIECES_TEXTURE,
                x,
                x + ConnectFourGameScreen.PIECE_WIDTH,
                y,
                y + ConnectFourGameScreen.PIECE_HEIGHT,
                0,
                (float) xOffset / ConnectFourGameScreen.TEXTURE_PIECES_WIDTH,
                (float) (xOffset + ConnectFourGameScreen.TEXTURE_PIECE_WIDTH) / ConnectFourGameScreen.TEXTURE_PIECES_WIDTH,
                0.0f / ConnectFourGameScreen.TEXTURE_PIECES_HEIGHT,
                (float) ConnectFourGameScreen.TEXTURE_PIECE_HEIGHT / ConnectFourGameScreen.TEXTURE_PIECES_HEIGHT,
                1.0f,
                1.0f,
                1.0f,
                transparent ? 0.5f : 1.0f
            );
        }
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
        private static final int TEXTURE_PIECES_WIDTH = TEXTURE_PIECE_WIDTH + TEXTURE_PIECE_WIDTH; // red and yellow
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
            super(Component.translatable("fourInARowGame.title", game.opponent.getProfile().getName()));
            this.game = game;
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            int startX = (this.width - BOARD_WIDTH) / 2;
            int startY = (this.height - BOARD_HEIGHT) / 2;

            graphics.drawString(this.font, Component.translatable("fourInARowGame.pieceSet", Piece.name(this.game.yourPiece)), startX, startY - 20, 0xff_ffffff);
            graphics.drawString(this.font, this.title, startX, startY - 10, 0xff_ffffff);
            Component moveTranslate = this.game.isYourTurn() ? Component.translatable("fourInARowGame.yourMove") : Component.translatable("fourInARowGame.opponentMove");
            graphics.drawString(this.font, moveTranslate, startX + BOARD_WIDTH - this.font.width(moveTranslate), startY - 10, 0xff_ffffff);

            graphics.blit(
                BOARD_TEXTURE,
                startX,
                startY,
                BOARD_WIDTH,
                BOARD_HEIGHT,
                0,
                0,
                TEXTURE_BOARD_WIDTH,
                TEXTURE_BOARD_HEIGHT,
                TEXTURE_BOARD_WIDTH,
                TEXTURE_BOARD_HEIGHT
            );

            for (int x = 0; x < ConnectFourGame.WIDTH; x++) {
                for (int y = 0; y < ConnectFourGame.HEIGHT; y++) {
                    Piece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (ConnectFourGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, this.game.board[x][y], false);
                }
            }

            int boardMinX = startX + BOARD_BORDER_WIDTH;
            int boardMaxX = startX + BOARD_WIDTH - BOARD_BORDER_WIDTH * 2;
            int boardMaxY = startY + BOARD_HEIGHT;
            if (this.game.canMove() && boardMinX <= mouseX && mouseX < boardMaxX && mouseY < boardMaxY) {
                int x = (mouseX - boardMinX) / SLOT_WIDTH;
                int y = this.game.getPlacementY(x);
                if (y < ConnectFourGame.HEIGHT) {
                    Piece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (ConnectFourGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, this.game.yourPiece, true);
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
