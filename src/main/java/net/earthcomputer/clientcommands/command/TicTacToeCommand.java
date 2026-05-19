package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.earthcomputer.clientcommands.interfaces.ISafeZoneScreen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class TicTacToeCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.createCommandTree());
    }

    public static void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet) {
        String sender = packet.sender();
        UUID senderUUID = packet.senderUUID();
        if (sender == null || senderUUID == null) {
            return;
        }

        TicTacToeGame game = TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.getActiveGame(senderUUID);
        if (game == null) {
            return;
        }
        if (game.putMark(packet.x(), packet.y(), game.yourMarks.opposite())) {
            TicTacToeGame.Mark winner = game.getWinner();
            if (winner == game.yourMarks.opposite()) {
                TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.onLost(sender, senderUUID);
            } else if (game.isDrawn()) {
                TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.onDraw(sender, senderUUID);
            } else {
                TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.onMove(sender);
            }
        }
    }

    public static class TicTacToeGame {
        public final PlayerInfo opponent;

        private final @Nullable Mark[][] board = new Mark[3][3];
        private final Mark yourMarks;
        private boolean yourTurn;

        public TicTacToeGame(PlayerInfo opponent, Mark yourMarks) {
            this.opponent = opponent;
            this.yourMarks = yourMarks;
            this.yourTurn = yourMarks == Mark.CROSS;
        }

        public boolean putMark(byte x, byte y, Mark mark) {
            if (this.yourMarks == mark == this.yourTurn) {
                if (this.board[x][y] == null) {
                    this.board[x][y] = mark;
                    this.yourTurn = !this.yourTurn;
                    return true;
                }
            }
            return false;
        }

        @Nullable
        public Mark getWinner() {
            for (byte x = 0; x < 3; x++) {
                if (this.board[x][0] == this.board[x][1] && this.board[x][1] == this.board[x][2] && this.board[x][0] != null) {
                    return this.board[x][0];
                }
                if (this.board[0][x] == this.board[1][x] && this.board[1][x] == this.board[2][x] && this.board[0][x] != null) {
                    return this.board[0][x];
                }
            }
            if (this.board[0][0] == this.board[1][1] && this.board[1][1] == this.board[2][2] && this.board[0][0] != null) {
                return this.board[0][0];
            }
            if (this.board[0][2] == this.board[1][1] && this.board[1][1] == this.board[2][0] && this.board[0][2] != null) {
                return this.board[0][2];
            }
            return null;
        }

        public boolean isDrawn() {
            for (Mark[] marks : this.board) {
                for (Mark mark : marks) {
                    if (mark != null) {
                        return false;
                    }
                }
            }

            return true;
        }

        public enum Mark {
            NOUGHT(Component.translatable("ticTacToeGame.noughts")),
            CROSS(Component.translatable("ticTacToeGame.crosses"));

            private final Component name;

            Mark(Component name) {
                this.name = name;
            }

            public Mark opposite() {
                return this == NOUGHT ? CROSS : NOUGHT;
            }
        }
    }

    public static class TicTacToeGameScreen extends Screen implements ISafeZoneScreen {
        private final TicTacToeGame game;

        private static final Identifier GRID_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/tic_tac_toe/grid.png");
        private static final Identifier MARKS_TEXTURE = Identifier.fromNamespaceAndPath("clientcommands", "textures/tic_tac_toe/marks.png");

        private static final int GRID_SIZE_TEXTURE = 512;
        private static final int MARK_SIZE_TEXTURE = 152;

        private static final int GRID_SIZE = 256;
        private static final int CELL_SIZE = 80;
        private static final int BORDER_SIZE = 8;
        private static final int MARK_SIZE = 76;
        private static final int PADDING = 2;

        public TicTacToeGameScreen(TicTacToeGame game) {
            super(Component.translatable("ticTacToeGame.title", game.opponent.getProfile().name()));
            this.game = game;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractRenderState(graphics, mouseX, mouseY, a);
            int startX = (this.width - GRID_SIZE) / 2;
            int startY = (this.height - GRID_SIZE) / 2;

            graphics.text(this.font, this.title, startX, startY - 20, 0xff_ffffff);
            graphics.text(this.font, Component.translatable("ticTacToeGame.playingWith", this.game.yourMarks.name), startX, startY - 10, 0xff_ffffff);

            graphics.blit(RenderPipelines.GUI_TEXTURED, GRID_TEXTURE, startX, startY, 0, 0, GRID_SIZE, GRID_SIZE, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE);
            TicTacToeGame.@Nullable Mark[][] board = this.game.board;

            for (byte x = 0; x < 3; x++) {
                for (byte y = 0; y < 3; y++) {
                    TicTacToeGame.Mark mark = board[x][y];
                    if (mark == null) {
                        continue;
                    }
                    int offset = switch (mark) {
                        case NOUGHT -> 0;
                        case CROSS -> MARK_SIZE_TEXTURE;
                    };
                    graphics.blit(RenderPipelines.GUI_TEXTURED, MARKS_TEXTURE, startX + (CELL_SIZE + BORDER_SIZE) * x + PADDING, startY + (CELL_SIZE + BORDER_SIZE) * y + PADDING, offset, 0, MARK_SIZE, MARK_SIZE, MARK_SIZE_TEXTURE, MARK_SIZE_TEXTURE, 2 * MARK_SIZE_TEXTURE, MARK_SIZE_TEXTURE);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            int startX = (this.width - GRID_SIZE) / 2;
            int startY = (this.height - GRID_SIZE) / 2;
            if (event.x() < startX || event.x() > startX + GRID_SIZE || event.y() < startY || event.y() > startY + GRID_SIZE) {
                return super.mouseClicked(event, doubleClick);
            }
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            double relativeX = event.x() - startX;
            byte x = (byte) (relativeX / (CELL_SIZE + BORDER_SIZE));
            if (relativeX > (CELL_SIZE + BORDER_SIZE) * (x + 1) - BORDER_SIZE) {
                return false;
            }
            double relativeY = event.y() - startY;
            byte y = (byte) (relativeY / (CELL_SIZE + BORDER_SIZE));
            if (relativeY > (CELL_SIZE + BORDER_SIZE) * (y + 1) - BORDER_SIZE) {
                return false;
            }

            if (this.game.putMark(x, y, this.game.yourMarks)) {
                try {
                    PutTicTacToeMarkC2CPacket packet = new PutTicTacToeMarkC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().name(), Minecraft.getInstance().getConnection().getLocalGameProfile().id(), x, y);
                    C2CPacketHandler.getInstance().sendPacket(packet, this.game.opponent);
                } catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                }
                if (this.game.getWinner() == this.game.yourMarks) {
                    TwoPlayerGame.TIC_TAC_TOE_GAME_TYPE.onWon(this.game.opponent.getProfile().name(), this.game.opponent.getProfile().id());
                }
                return true;
            }
            return false;
        }

        @Override
        public int getSafeZoneWidth() {
            return GRID_SIZE;
        }

        @Override
        public int getSafeZoneHeight() {
            return GRID_SIZE;
        }
    }
}
