package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
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

public class TicTacToeCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(TwoPlayerGameType.TIC_TAC_TOE_GAME_TYPE.createCommandTree());
    }

    public static void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet) {
        String sender = packet.sender();
        TicTacToeGame game = TwoPlayerGameType.TIC_TAC_TOE_GAME_TYPE.getActiveGame(sender);
        if (game == null) {
            return;
        }
        if (game.putMark(packet.x(), packet.y(), game.yourMarks.opposite())) {
            if (game.getWinner() == game.yourMarks.opposite()) {
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.putTicTacToeMarkC2CPacket.incoming.lost", sender));
                TwoPlayerGameType.TIC_TAC_TOE_GAME_TYPE.getActiveGames().remove(sender);
                return;
            }
            MutableComponent component = Component.translatable("c2cpacket.putTicTacToeMarkC2CPacket.incoming", sender);
            component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctictactoe open " + sender))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/ctictactoe open " + sender))));
            Minecraft.getInstance().gui.getChat().addMessage(component);
        }
    }

    public static class TicTacToeGame extends TwoPlayerGame<TicTacToeGameScreen> {
        public final PlayerInfo opponent;

        private final Mark[][] board = new Mark[3][3];
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

        @Override
        public TicTacToeGameScreen createScreen() {
            return new TicTacToeGameScreen(this);
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

    public static class TicTacToeGameScreen extends Screen {
        private final TicTacToeGame game;

        private static final ResourceLocation GRID_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/tic_tac_toe/grid.png");
        private static final ResourceLocation MARKS_TEXTURE = ResourceLocation.fromNamespaceAndPath("clientcommands", "textures/tic_tac_toe/marks.png");

        private static final int GRID_SIZE_TEXTURE = 512;
        private static final int MARK_SIZE_TEXTURE = 152;

        private static final int GRID_SIZE = 256;
        private static final int CELL_SIZE = 80;
        private static final int BORDER_SIZE = 8;
        private static final int MARK_SIZE = 76;
        private static final int PADDING = 2;

        private TicTacToeGameScreen(TicTacToeGame game) {
            super(Component.translatable("ticTacToeGame.title", game.opponent.getProfile().getName()));
            this.game = game;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            int startX = (this.width - GRID_SIZE) / 2;
            int startY = (this.height - GRID_SIZE) / 2;

            guiGraphics.drawString(this.font, this.title, startX, startY - 20, 0xff_ffffff);
            guiGraphics.drawString(this.font, Component.translatable("ticTacToeGame.playingWith", this.game.yourMarks.name), startX, startY - 10, 0xff_ffffff);

            guiGraphics.blit(GRID_TEXTURE, startX, startY, GRID_SIZE, GRID_SIZE, 0, 0, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE, GRID_SIZE_TEXTURE);
            TicTacToeGame.Mark[][] board = this.game.board;

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
                    guiGraphics.blit(MARKS_TEXTURE, startX + (CELL_SIZE + BORDER_SIZE) * x + PADDING, startY + (CELL_SIZE + BORDER_SIZE) * y + PADDING, MARK_SIZE, MARK_SIZE, offset, 0, MARK_SIZE_TEXTURE, MARK_SIZE_TEXTURE, 2 * MARK_SIZE_TEXTURE, MARK_SIZE_TEXTURE);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = (this.width - GRID_SIZE) / 2;
            int startY = (this.height - GRID_SIZE) / 2;
            if (mouseX < startX || mouseX > startX + GRID_SIZE || mouseY < startY || mouseY > startY + GRID_SIZE) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            double relativeX = mouseX - startX;
            byte x = (byte) (relativeX / (CELL_SIZE + BORDER_SIZE));
            if (relativeX > (CELL_SIZE + BORDER_SIZE) * (x + 1) - BORDER_SIZE) {
                return false;
            }
            double relativeY = mouseY - startY;
            byte y = (byte) (relativeY / (CELL_SIZE + BORDER_SIZE));
            if (relativeY > (CELL_SIZE + BORDER_SIZE) * (y + 1) - BORDER_SIZE) {
                return false;
            }

            if (this.game.putMark(x, y, this.game.yourMarks)) {
                try {
                    PutTicTacToeMarkC2CPacket packet = new PutTicTacToeMarkC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), x, y);
                    C2CPacketHandler.getInstance().sendPacket(packet, this.game.opponent);
                } catch (CommandSyntaxException e) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                }
                if (this.game.getWinner() == this.game.yourMarks) {
                    TwoPlayerGameType.TIC_TAC_TOE_GAME_TYPE.getActiveGames().remove(this.game.opponent.getProfile().getName());
                }
                return true;
            }
            return false;
        }
    }
}
