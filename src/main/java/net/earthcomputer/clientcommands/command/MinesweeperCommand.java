package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    private static final SimpleCommandExceptionType TOO_MANY_MINES_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cminesweeper.too_many_mines"));

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

        source.getClient().tell(() -> source.getClient().setScreen(new MinesweeperGameScreen(width, height, mines)));

        return Command.SINGLE_SUCCESS;
    }
}

class MinesweeperGameScreen extends Screen {
    private static final ResourceLocation MINESWEEPER_ATLAS = new ResourceLocation("clientcommands:textures/minesweeper_atlas.png");

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

    int boardWidth;
    int boardHeight;
    int mines;
    int ticksPlaying;
    byte[] board;
    int gameWidth;
    int gameHeight;
    int topLeftX;
    int topLeftY;
    @Nullable
    Integer dragging;
    @Nullable
    Vector2i deathCoords;
    int minesLeft;
    int emptyTilesRemaining;

    MinesweeperGameScreen(int width, int height, int mines) {
        super(Component.translatable("minesweeperGame.title"));
        this.boardWidth = width;
        this.boardHeight = height;
        this.mines = mines;
        this.ticksPlaying = 0;
        this.board = new byte[boardWidth * boardHeight];
        this.gameWidth = boardWidth * 16 + 20;
        this.gameHeight = boardHeight * 16 + 20;
        this.dragging = null;
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

        graphics.drawString(minecraft.font, "Mines Left: " + minesLeft, topLeftX, topLeftY - 10, 0xff_ffffff);
        graphics.drawCenteredString(minecraft.font, title.getString(), topLeftX + gameWidth / 2, topLeftY - 20, 0xff_ffffff);
        {
            String str = "Time Played: " + Math.ceilDiv(ticksPlaying, 20) + "s";
            graphics.drawString(minecraft.font, str, topLeftX + gameWidth - minecraft.font.width(str), topLeftY - 10, deathCoords != null ? ChatFormatting.RED.getColor() : (emptyTilesRemaining == 0 ? ChatFormatting.GREEN.getColor() : ChatFormatting.WHITE.getColor()));
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
        graphics.blit(MINESWEEPER_ATLAS, topLeftX + x, topLeftY + y, width, height, uv.x, uv.y, width, height, 128, 64);
    }

    @Override
    public void tick() {
        if (ticksPlaying > 0 && gameActive()) {
            ticksPlaying += 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        dragging = button;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;

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
        Random random = new Random();

        for (int i = 0; i < mines; i++) {
            int x = random.nextInt(boardWidth);
            int y = random.nextInt(boardHeight);

            // too close to the clicked position
            if (Mth.abs(avoidX - x) <= 1 && Mth.abs(avoidY - y) <= 1) {
                i--;
                continue;
            }

            // already a mine
            if ((board[y * boardWidth + x] & 0b1100) >>> 2 == 2) {
                i--;
                continue;
            }

            incrementWarning(x - 1, y - 1);
            incrementWarning(x, y - 1);
            incrementWarning(x + 1, y - 1);

            incrementWarning(x - 1, y);
            board[y * boardWidth + x] = 0b000_10_0_0;
            incrementWarning(x + 1, y);

            incrementWarning(x - 1, y + 1);
            incrementWarning(x, y + 1);
            incrementWarning(x + 1, y + 1);
        }
    }

    private void incrementWarning(int x, int y) {
        if (isWithinBounds(x, y)) {
            int idx = y * boardWidth + x;
            byte original_tile = board[idx];
            if ((original_tile & 0b1100) >>> 2 == 1) {
                // increment warning quantity
                board[idx] += 0b0_001_00_0_0;
            } else if ((original_tile & 0b1100) >>> 2 == 0) {
                // set to warning tile (and since it was empty beforehand, we make it have a quantity of 1)
                board[idx] = 0b0_000_01_0_0;
            }
        }
    }

    private boolean isWithinBounds(int x, int y) {
        return 0 <= x && x < boardWidth && 0 <= y && y < boardHeight;
    }

    private void click(int x, int y) {
        byte tile = board[y * boardWidth + x];
        boolean flagged = (tile & 2) > 0;
        boolean covered = (tile & 1) == 0;
        int type = (tile & 0b1100) >>> 2;
        if (!covered || flagged) {
            return;
        }

        if (type == 1) {
            // set uncovered
            board[y * boardWidth + x] |= 0b1;
            emptyTilesRemaining -= 1;
        } else if (type == 2) {
            // set uncovered
            board[y * boardWidth + x] |= 0b1;
            deathCoords = new Vector2i(x, y);
        } else {
            // set uncovered
            board[y * boardWidth + x] |= 0b1;
            emptyTilesRemaining -= 1;
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
                        int pos = possibleNeighbour.y * boardWidth + possibleNeighbour.x;
                        byte value = board[pos];
                        // set uncovered
                        board[pos] |= 0b1;
                        if ((value & 0b1) == 0) {
                            emptyTilesRemaining -= 1;
                            // if it's an empty tile, we put it in the queue to go activate all its neighbours
                            if ((value & 0b11_0_0) >>> 2 == 0) {
                                queue[++queueIdx] = pos;
                            }
                        }
                    }
                }
            }
        }
    }

    private void flag(int x, int y) {
        if ((board[y * boardWidth + x] & 0b1) > 0) {
            return;
        }

        minesLeft -= ((board[y * boardWidth + x] ^= 0b1_0) & 0b1_0) > 0 ? 1 : -1;
    }

    private Vector2i getTileSprite(int x, int y, boolean hovered) {
        byte tile = board[y * boardWidth + x];
        boolean flagged = (tile & 2) > 0;
        boolean covered = (tile & 1) == 0;
        int type = (tile & 0b1100) >>> 2;
        int warning_quantity = (tile & 0b1110000) >>> 4;

        if (deathCoords != null && type == 2 && !flagged) {
            return new Vector2i(x, y).equals(deathCoords) ? RED_MINE_TILE_UV : MINE_TILE_UV;
        }

        if (flagged) {
            return hovered && deathCoords == null ? HOVERED_FLAGGED_TILE_UV : (deathCoords != null && type != 2 ? NOT_A_MINE_TILE_UV : FLAGGED_TILE_UV);
        }

        if (covered) {
            return hovered && deathCoords == null ? (dragging != null && dragging == 0 ? EMPTY_TILE_UV : HOVERED_TILE_UV) : TILE_UV;
        }

        if (type == 0) {
            return EMPTY_TILE_UV;
        } else {
            return switch (warning_quantity) {
                case 0 -> ONE_TILE_UV;
                case 1 -> TWO_TILE_UV;
                case 2 -> THREE_TILE_UV;
                case 3 -> FOUR_TILE_UV;
                case 4 -> FIVE_TILE_UV;
                case 5 -> SIX_TILE_UV;
                case 6 -> SEVEN_TILE_UV;
                default -> EIGHT_TILE_UV;
            };
        }
    }
}
