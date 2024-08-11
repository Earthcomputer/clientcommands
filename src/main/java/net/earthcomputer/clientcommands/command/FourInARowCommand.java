package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutFourInARowPieceC2CPacket;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
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

public class FourInARowCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.createCommandTree());
    }

    public static void onPutFourInARowPieceC2CPacket(PutFourInARowPieceC2CPacket packet) {
        String sender = packet.sender();
        FourInARowGame game = TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGame(sender);
        if (game == null) {
            return;
        }
        game.onMove(packet.x());
    }

    public static class FourInARowGame extends TwoPlayerGame<FourInARowGameScreen> {
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

        public FourInARowGame(PlayerInfo opponent, byte yourPiece) {
            this.opponent = opponent;
            this.yourPiece = yourPiece;
            this.activePiece = Piece.RED;
            this.board = new byte[WIDTH][HEIGHT];
            this.winner = 0;
        }

        public void onMove(int x) {
            if (!isGameActive()) {
                LOGGER.warn("Tried to add piece to the already completed game with {}.", opponent.getProfile().getName());
                return;
            }

            if (!addPiece(x, activePiece)) {
                LOGGER.warn("Failed to add piece to your Four in a Row game with {}.", opponent.getProfile().getName());
                return;
            }

            if (isYourTurn()) {
                try {
                    PutFourInARowPieceC2CPacket packet = new PutFourInARowPieceC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), x);
                    C2CPacketHandler.getInstance().sendPacket(packet, opponent);
                } catch (CommandSyntaxException e) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                }
            }

            String sender = opponent.getProfile().getName();
            activePiece = Piece.opposite(activePiece);
            if ((winner = getWinner()) != 0) {
                if (winner == yourPiece) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("fourInARowGame.won", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                } else if (winner == Piece.opposite(yourPiece)) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.putFourInARowPieceC2CPacket.incoming.lost", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                } else if (winner == 3) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("fourInARowGame.draw", sender));
                    TwoPlayerGameType.FOUR_IN_A_ROW_GAME_TYPE.getActiveGames().remove(sender);
                }
            } else {
                if (isYourTurn()) {
                    MutableComponent component = Component.translatable("c2cpacket.putFourInARowPieceC2CPacket.incoming", sender);
                    component.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cfourinarow open " + sender))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/cfourinarow open " + sender))));
                    Minecraft.getInstance().gui.getChat().addMessage(component);
                }
            }
        }

        public boolean isYourTurn() {
            return activePiece == yourPiece;
        }

        public boolean isGameActive() {
            return winner == 0;
        }

        public boolean canMove() {
            return isYourTurn() && isGameActive();
        }

        public boolean addPiece(int x, byte piece) {
            int y;
            if (isValidRow(x) && (y = getPlacementY(x)) < HEIGHT) {
                // this only exists to signify that each bit represents a different piece
                board[x][y] |= piece;
                return true;
            }
            return false;
        }

        // The `&` bitmasks only work because each piece has a unique bit, therefore making each bit represent if that piece is in that position, doing an & on all of them tells you which pieces are represented in every space.
        private byte getWinner() {
            // check horizontally
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if ((board[x][y] & board[x + 1][y] & board[x + 2][y] & board[x + 3][y]) > 0) {
                        return board[x][y];
                    }
                }
            }

            // check vertically
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if ((board[x][y] & board[x][y + 1] & board[x][y + 2] & board[x][y + 3]) > 0) {
                        return board[x][y];
                    }
                }
            }

            // check horizontally (northeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 0; y < HEIGHT - 3; y++) {
                    if ((board[x][y] & board[x + 1][y + 1] & board[x + 2][y + 2] & board[x + 3][y + 3]) > 0) {
                        return board[x][y];
                    }
                }
            }

            // check horizontally (southeast)
            for (int x = 0; x < WIDTH - 3; x++) {
                for (int y = 3; y < HEIGHT; y++) {
                    if ((board[x][y] & board[x + 1][y - 1] & board[x + 2][y - 2] & board[x + 3][y - 3]) > 0) {
                        return board[x][y];
                    }
                }
            }

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (board[x][y] == 0) {
                        // still a space to play
                        return 0;
                    }
                }
            }

            // no spaces left, game ends in a draw
            return Piece.RED | Piece.YELLOW;
        }

        @Override
        public FourInARowGameScreen createScreen() {
            return new FourInARowGameScreen(this);
        }

        public static boolean isValidRow(int x) {
            return 0 <= x && x < WIDTH;
        }

        public int getPlacementY(int x) {
            int y = 0;
            for (byte piece : board[x]) {
                if (piece == 0) {
                    break;
                } else {
                    y++;
                }
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
                FourInARowGameScreen.PIECES_TEXTURE,
                x,
                x + FourInARowGameScreen.PIECE_WIDTH,
                y,
                y + FourInARowGameScreen.PIECE_HEIGHT,
                0,
                (float) xOffset / FourInARowGameScreen.TEXTURE_PIECES_WIDTH,
                (float) (xOffset + FourInARowGameScreen.TEXTURE_PIECE_WIDTH) / FourInARowGameScreen.TEXTURE_PIECES_WIDTH,
                0.0f / FourInARowGameScreen.TEXTURE_PIECES_HEIGHT,
                (float) FourInARowGameScreen.TEXTURE_PIECE_HEIGHT / FourInARowGameScreen.TEXTURE_PIECES_HEIGHT,
                1.0f,
                1.0f,
                1.0f,
                transparent ? 0.5f : 1.0f
            );
        }
    }

    public static class FourInARowGameScreen extends Screen {
        private final FourInARowGame game;

        private static final ResourceLocation BOARD_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/four_in_a_row/board.png");
        private static final ResourceLocation PIECES_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/four_in_a_row/pieces.png");

        private static final int SCALE = 4;

        private static final int TEXTURE_PIECE_WIDTH = 16;
        private static final int TEXTURE_PIECE_HEIGHT = 16;
        private static final int TEXTURE_BOARD_BORDER_WIDTH = 1;
        private static final int TEXTURE_BOARD_BORDER_HEIGHT = 1;
        private static final int TEXTURE_SLOT_BORDER_WIDTH = 1;
        private static final int TEXTURE_SLOT_BORDER_HEIGHT = 1;
        private static final int TEXTURE_SLOT_WIDTH = TEXTURE_PIECE_WIDTH + 2 * TEXTURE_SLOT_BORDER_WIDTH;
        private static final int TEXTURE_SLOT_HEIGHT = TEXTURE_PIECE_HEIGHT + 2 * TEXTURE_SLOT_BORDER_HEIGHT;
        private static final int TEXTURE_BOARD_WIDTH = TEXTURE_SLOT_WIDTH * FourInARowGame.WIDTH + TEXTURE_BOARD_BORDER_WIDTH * 2;
        private static final int TEXTURE_BOARD_HEIGHT = TEXTURE_SLOT_HEIGHT * FourInARowGame.HEIGHT + TEXTURE_BOARD_BORDER_HEIGHT * 2;
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
        
        public FourInARowGameScreen(FourInARowGame game) {
            super(Component.translatable("fourInARowGame.title", game.opponent.getProfile().getName()));
            this.game = game;
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            int startX = (width - BOARD_WIDTH) / 2;
            int startY = (height - BOARD_HEIGHT) / 2;

            graphics.drawString(font, Component.translatable("fourInARowGame.pieceSet", Piece.name(game.yourPiece)), startX, startY - 20, 0xff_ffffff);
            graphics.drawString(font, title, startX, startY - 10, 0xff_ffffff);
            Component moveTranslate = game.isYourTurn() ? Component.translatable("fourInARowGame.yourMove") : Component.translatable("fourInARowGame.opponentMove");
            graphics.drawString(font, moveTranslate, startX + BOARD_WIDTH - font.width(moveTranslate), startY - 10, 0xff_ffffff);

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

            for (int x = 0; x < FourInARowGame.WIDTH; x++) {
                for (int y = 0; y < FourInARowGame.HEIGHT; y++) {
                    Piece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (FourInARowGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, game.board[x][y], false);
                }
            }

            int boardMinX = startX + BOARD_BORDER_WIDTH;
            int boardMaxX = startX + BOARD_WIDTH - BOARD_BORDER_WIDTH * 2;
            int boardMaxY = startY + BOARD_HEIGHT;
            if (game.canMove() && boardMinX <= mouseX && mouseX < boardMaxX && mouseY < boardMaxY) {
                int x = (mouseX - boardMinX) / SLOT_WIDTH;
                int y = game.getPlacementY(x);
                if (y < FourInARowGame.HEIGHT) {
                    Piece.render(graphics, startX + BOARD_BORDER_WIDTH + SLOT_WIDTH * x + SLOT_BORDER_WIDTH, startY + BOARD_BORDER_HEIGHT + SLOT_HEIGHT * (FourInARowGame.HEIGHT - 1 - y) + SLOT_BORDER_HEIGHT, game.yourPiece, true);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = (width - BOARD_WIDTH) / 2;
            int startY = (height - BOARD_HEIGHT) / 2;

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
            if (game.canMove()) {
                game.onMove(x);
                return true;
            }

            return false;
        }
    }
}
