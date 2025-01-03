package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.Random;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class MinesweeperCommand {
    private static final SimpleCommandExceptionType TOO_MANY_MINES_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cminesweeper.tooManyMines"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cminesweeper")
            .executes(ctx -> minesweeper(ctx.getSource(), 9, 9, 10))
            .then(literal("beginner")
                .executes(ctx -> minesweeper(ctx.getSource(), 9, 9, 10)))
            .then(literal("intermediate")
                .executes(ctx -> minesweeper(ctx.getSource(), 16, 16, 40)))
            .then(literal("expert")
                .executes(ctx -> minesweeper(ctx.getSource(), 32, 16, 99)))
            .then(literal("custom")
                .then(argument("width", integer(3, 128))
                    .then(argument("height", integer(3, 128))
                        .then(argument("mines", integer(0, 128 * 128 - 9))
                            .executes(ctx -> minesweeper(ctx.getSource(), getInteger(ctx, "width"), getInteger(ctx, "height"), getInteger(ctx, "mines"))))))));
    }

    private static int minesweeper(FabricClientCommandSource source, int width, int height, int mines) throws CommandSyntaxException {
        if (mines > (width * height - 9)) {
            throw TOO_MANY_MINES_EXCEPTION.create();
        }

        source.getClient().schedule(() -> source.getClient().setScreen(new MinesweeperGameScreen(width, height, mines)));

        return Command.SINGLE_SUCCESS;
    }

    private static class MinesweeperGameScreen extends Screen {
        private static final ResourceLocation MINESWEEPER_ATLAS = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/minesweeper_atlas.png");
        private static final int MINESWEEPER_ATLAS_WIDTH = 128;
        private static final int MINESWEEPER_ATLAS_HEIGHT = 64;

        private static final Vector2i TOP_LEFT_UV = new Vector2i(0, 0);
        private static final Vector2i TOP_UV = new Vector2i(12, 0);
        private static final Vector2i TOP_RIGHT_UV = new Vector2i(28, 0);
        private static final Vector2i LEFT_UV = new Vector2i(0, 12);
        private static final Vector2i RIGHT_UV = new Vector2i(28, 12);
        private static final Vector2i BOTTOM_LEFT_UV = new Vector2i(0, 28);
        private static final Vector2i BOTTOM_UV = new Vector2i(12, 28);
        private static final Vector2i BOTTOM_RIGHT_UV = new Vector2i(28, 28);

        private static final Vector2i NOT_A_MINE_TILE_UV = new Vector2i(68, 32);
        private static final Vector2i MINE_TILE_UV = new Vector2i(52, 32);
        private static final Vector2i RED_MINE_TILE_UV = new Vector2i(84, 16);
        private static final Vector2i EMPTY_TILE_UV = new Vector2i(12, 12);
        private static final Vector2i HOVERED_TILE_UV = new Vector2i(100, 16);
        private static final Vector2i HOVERED_FLAGGED_TILE_UV = new Vector2i(36, 32);
        private static final Vector2i FLAGGED_TILE_UV = new Vector2i(100, 32);
        private static final Vector2i TILE_UV = new Vector2i(84, 32);
        private static final Vector2i ONE_TILE_UV = new Vector2i(36, 0);
        private static final Vector2i TWO_TILE_UV = new Vector2i(52, 0);
        private static final Vector2i THREE_TILE_UV = new Vector2i(68, 0);
        private static final Vector2i FOUR_TILE_UV = new Vector2i(84, 0);
        private static final Vector2i FIVE_TILE_UV = new Vector2i(100, 0);
        private static final Vector2i SIX_TILE_UV = new Vector2i(36, 16);
        private static final Vector2i SEVEN_TILE_UV = new Vector2i(52, 16);
        private static final Vector2i EIGHT_TILE_UV = new Vector2i(68, 16);
        private static final Vector2i[] WARNING_TILE_UV = new Vector2i[] {
            ONE_TILE_UV,
            TWO_TILE_UV,
            THREE_TILE_UV,
            FOUR_TILE_UV,
            FIVE_TILE_UV,
            SIX_TILE_UV,
            SEVEN_TILE_UV,
            EIGHT_TILE_UV
        };

        private static final byte EMPTY_TILE_TYPE = 0;
        private static final byte WARNING_TILE_TYPE = 1;
        private static final byte MINE_TILE_TYPE = 2;

        private static final Random random = new Random();

        private final int boardWidth;
        private final int boardHeight;
        private final int mines;
        private int ticksPlaying;
        /**
         * Each byte on the board follows a spec of what each collection of bits represents
         * <pre>
         * 0 0
         * </pre>
         * These bits are unused
         * <p>
         * <pre>
         *      _ _ _
         * </pre>
         * These bits are used to refer to the number displayed on warning tiles (technically one less): 0b000 represents a 1 tile, 0b001 represents a 2 tile, and so on.
         * <p>
         * <pre>
         *            _ _
         * </pre>
         * These bits represent the type of tile that is there; 0b00 means an empty tile, 0b01 means a warning tile, and 0b10 means a mine.
         * <p>
         * <pre>
         *                _
         * </pre>
         * <p>
         * This bit represents the flag... flag. If it is 1, the tile is flagged, if it is 0, the tile is not flagged.
         * <pre>
         *                  _
         * </pre>
         * This bit represents if the tile has been uncovered, by default, all tiles are covered.
         */
        private final byte[] board;
        private final int gameWidth;
        private final int gameHeight;
        private int topLeftX;
        private int topLeftY;
        @Nullable
        private Vector2i deathCoords;
        private int minesLeft;
        private int emptyTilesRemaining;

        MinesweeperGameScreen(int width, int height, int mines) {
            super(Component.translatable("minesweeperGame.title"));
            this.boardWidth = width;
            this.boardHeight = height;
            this.mines = mines;
            this.ticksPlaying = 0;
            this.board = new byte[boardWidth * boardHeight];
            this.gameWidth = boardWidth * 16 + 20;
            this.gameHeight = boardHeight * 16 + 20;
            this.deathCoords = null;
            this.minesLeft = mines;
            this.emptyTilesRemaining = width * height - mines;
        }

        @Override
        protected void init() {
            this.topLeftX = (this.width - gameWidth) / 2;
            this.topLeftY = (this.height - gameHeight) / 2;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
            renderBackground(graphics, mouseX, mouseY, tickDelta);

            graphics.drawString(minecraft.font, I18n.get("minesweeperGame.minesLeft", minesLeft), topLeftX, topLeftY - 10, 0xFFFFFF);
            graphics.drawCenteredString(minecraft.font, title.getString(), topLeftX + gameWidth / 2, topLeftY - 20, 0xFFFFFF);
            {
                String str = I18n.get("minesweeperGame.timePlayed", Math.ceilDiv(ticksPlaying, 20));
                int color;
                if (deathCoords != null) {
                    color = 0xFF5555;
                } else if (emptyTilesRemaining <= 0) {
                    color = 0x55FF55;
                } else {
                    color = 0xFFFFFF;
                }
                graphics.drawString(minecraft.font, str, topLeftX + gameWidth - minecraft.font.width(str), topLeftY - 10, color);
            }

            blitSprite(graphics, TOP_LEFT_UV, 0, 0, 12, 12);
            for (int i = 0; i < boardWidth; i++) {
                blitSprite(graphics, TOP_UV, 12 + i * 16, 0, 16, 12);
            }
            blitSprite(graphics, TOP_RIGHT_UV, 12 + boardWidth * 16, 0, 8, 12);
            for (int i = 0; i < boardHeight; i++) {
                blitSprite(graphics, LEFT_UV, 0, 12 + i * 16, 12, 16);
                blitSprite(graphics, RIGHT_UV, 12 + boardWidth * 16, 12 + i * 16, 8, 16);
            }
            blitSprite(graphics, BOTTOM_LEFT_UV, 0, 12 + boardHeight * 16, 12, 8);
            for (int i = 0; i < boardWidth; i++) {
                blitSprite(graphics, BOTTOM_UV, 12 + i * 16, 12 + boardHeight * 16, 16, 8);
            }
            blitSprite(graphics, BOTTOM_RIGHT_UV, 12 + boardWidth * 16, 12 + boardHeight * 16, 8, 8);

            for (int x = 0; x < boardWidth; x++) {
                for (int y = 0; y < boardHeight; y++) {
                    boolean hovered = Mth.floorDiv(mouseX - topLeftX - 12, 16) == x && Mth.floorDiv(mouseY - topLeftY - 12, 16) == y;
                    blitSprite(graphics, getTileSprite(x, y, hovered),  x * 16 + 12, y * 16 + 12, 16, 16);
                }
            }
        }

        public void blitSprite(GuiGraphics graphics, Vector2i uv, int x, int y, int width, int height) {
            graphics.blit(RenderType::guiTextured, MINESWEEPER_ATLAS, topLeftX + x, topLeftY + y, uv.x, uv.y, width, height, MINESWEEPER_ATLAS_WIDTH, MINESWEEPER_ATLAS_HEIGHT);
        }

        @Override
        public void tick() {
            if (ticksPlaying > 0 && gameActive()) {
                ticksPlaying++;
            }
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            int tileX = Mth.floorDiv((int) (mouseX - topLeftX - 12), 16);
            int tileY = Mth.floorDiv((int) (mouseY - topLeftY - 12), 16);

            if (isWithinBounds(tileX, tileY) && gameActive()) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    if (ticksPlaying == 0) {
                        generateMines(tileX, tileY);
                        ticksPlaying = 1;
                    }

                    click(tileX, tileY);

                    assert minecraft != null && minecraft.player != null;
                    if (emptyTilesRemaining <= 0) {
                        minecraft.player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0f, 2.0f);
                    } else if (deathCoords != null) {
                        minecraft.player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1.0f, 1.0f);
                    }
                } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    flag(tileX, tileY);
                }
            }

            return true;
        }

        private boolean gameActive() {
            return deathCoords == null && emptyTilesRemaining > 0;
        }

        private void generateMines(int avoidX, int avoidY) {
            for (int i = 0; i < mines; i++) {
                int x = random.nextInt(boardWidth);
                int y = random.nextInt(boardHeight);

                // too close to the clicked position
                if (Mth.abs(avoidX - x) <= 1 && Mth.abs(avoidY - y) <= 1) {
                    i--;
                    continue;
                }

                if (tileType(getTile(x, y)) == MINE_TILE_TYPE) {
                    i--;
                    continue;
                }

                incrementWarning(x - 1, y - 1);
                incrementWarning(x, y - 1);
                incrementWarning(x + 1, y - 1);

                incrementWarning(x - 1, y);
                setTile(x, y, createTile(true, false, MINE_TILE_TYPE, null));
                incrementWarning(x + 1, y);

                incrementWarning(x - 1, y + 1);
                incrementWarning(x, y + 1);
                incrementWarning(x + 1, y + 1);
            }
        }

        private void incrementWarning(int x, int y) {
            if (isWithinBounds(x, y)) {
                byte originalTile = getTile(x, y);
                if (tileType(originalTile) == WARNING_TILE_TYPE) {
                    setTile(x, y, createTile(isCovered(originalTile), isFlagged(originalTile), WARNING_TILE_TYPE, warningQuantity(originalTile) + 1));
                } else if (tileType(originalTile) == EMPTY_TILE_TYPE) {
                    setTile(x, y, createTile(isCovered(originalTile), isFlagged(originalTile), WARNING_TILE_TYPE, 1));
                }
            }
        }

        private boolean isWithinBounds(int x, int y) {
            return 0 <= x && x < boardWidth && 0 <= y && y < boardHeight;
        }

        private void click(int x, int y) {
            byte tile = getTile(x, y);
            if (!isCovered(tile) || isFlagged(tile)) {
                return;
            }

            int type = tileType(tile);
            if (type == WARNING_TILE_TYPE) {
                uncover(x, y);
                emptyTilesRemaining--;
            } else if (type == MINE_TILE_TYPE) {
                uncover(x, y);
                deathCoords = new Vector2i(x, y);
            } else {
                uncover(x, y);
                emptyTilesRemaining--;
                // we need to leave room for the current tile in the queue
                int[] queue = new int[emptyTilesRemaining + 1];
                int queueIdx = 0;
                queue[0] = y * boardWidth + x;
                while (queueIdx >= 0) {
                    int idx = queue[queueIdx--];
                    int xPart = idx % boardWidth;
                    int yPart = idx / boardWidth;
                    for (Vector2i possibleNeighbour : new Vector2i[]{
                        new Vector2i(xPart - 1, yPart - 1),
                        new Vector2i(xPart, yPart - 1),
                        new Vector2i(xPart + 1, yPart - 1),

                        new Vector2i(xPart - 1, yPart),
                        new Vector2i(xPart + 1, yPart),

                        new Vector2i(xPart - 1, yPart + 1),
                        new Vector2i(xPart, yPart + 1),
                        new Vector2i(xPart + 1, yPart + 1),
                    }) {
                        if (isWithinBounds(possibleNeighbour.x, possibleNeighbour.y)) {
                            byte value = getTile(possibleNeighbour.x, possibleNeighbour.y);
                            uncover(possibleNeighbour.x, possibleNeighbour.y);
                            if (isCovered(value)) {
                                emptyTilesRemaining--;
                                // if it's an empty tile, we put it in the queue to go activate all its neighbours
                                if (tileType(value) == EMPTY_TILE_TYPE) {
                                    queue[++queueIdx] = possibleNeighbour.y * boardWidth + possibleNeighbour.x;
                                }
                            }
                        }
                    }
                }
            }
        }

        private void flag(int x, int y) {
            if (!isCovered(getTile(x, y))) {
                return;
            }

            // this code flips the state of the tile's flag, and then uses a ternary statement to either subtract 1, or subtract -1 from the amount of mines left.
            minesLeft -= ((board[y * boardWidth + x] ^= 0b1_0) & 0b1_0) > 0 ? 1 : -1;
        }

        private Vector2i getTileSprite(int x, int y, boolean hovered) {
            byte tile = getTile(x, y);
            boolean flagged = isFlagged(tile);
            boolean covered = isCovered(tile);
            int type = tileType(tile);
            int warningQuantity = warningQuantity(tile);

            if (deathCoords != null && type == MINE_TILE_TYPE && !flagged) {
                return new Vector2i(x, y).equals(deathCoords) ? RED_MINE_TILE_UV : MINE_TILE_UV;
            }

            if (flagged) {
                return hovered && deathCoords == null ? HOVERED_FLAGGED_TILE_UV : (deathCoords != null && type != MINE_TILE_TYPE ? NOT_A_MINE_TILE_UV : FLAGGED_TILE_UV);
            }

            if (covered) {
                return hovered && deathCoords == null ? (isDragging() ? EMPTY_TILE_UV : HOVERED_TILE_UV) : TILE_UV;
            }

            if (type == EMPTY_TILE_TYPE) {
                return EMPTY_TILE_UV;
            }

            return WARNING_TILE_UV[warningQuantity - 1];
        }

        private byte getTile(int x, int y) {
            return board[y * boardWidth + x];
        }

        private void setTile(int x, int y, byte value) {
            board[y * boardWidth + x] = value;
        }

        /**
         * @return 0 for an empty tile <br> 1 for a warning tile <br> 2 for a mine tile
         */
        private int tileType(byte tile) {
            return (tile & 0b1100) >>> 2;
        }

        /**
         * @return a value between 1 and 8 (inclusive) representing the amount of mines near the tile, if this is a warning tile
         */
        private int warningQuantity(byte tile) {
            return ((tile & 0b1110000) >>> 4) + 1;
        }

        private boolean isCovered(byte tile) {
            return (tile & 0b1) == 0;
        }

        private void uncover(int x, int y) {
            board[y * boardWidth + x] |= 1;
        }

        private boolean isFlagged(byte tile) {
            return (tile & 0b10) > 0;
        }

        private byte createTile(boolean covered, boolean flagged, int type, @Nullable Integer warningQuantity) {
            if (!covered && flagged) {
                throw new IllegalArgumentException("Tile cannot be uncovered and flagged at once");
            }

            if (type == WARNING_TILE_TYPE && (warningQuantity == null || warningQuantity < 1 || warningQuantity > 8)) {
                throw new IllegalArgumentException("Warning tiles must have a warning quantity between 1 and 8");
            }

            if (type != WARNING_TILE_TYPE && warningQuantity != null) {
                throw new IllegalArgumentException("Non-Warning tiles must have a null warning quantity");
            }

            if (type != EMPTY_TILE_TYPE && type != WARNING_TILE_TYPE && type != MINE_TILE_TYPE) {
                throw new IllegalArgumentException("Tile type must be empty, warning, or mine");
            }

            return (byte) ((covered ? 0 : 1) | ((flagged ? 1 : 0) << 1) | (type << 2) | ((warningQuantity == null ? 0 : warningQuantity - 1) << 4));
        }
    }
}
