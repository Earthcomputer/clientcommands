package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessGame;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import net.earthcomputer.clientcommands.c2c.packets.ChessBoardUpdateC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.ChessInviteC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.ChessResignC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;

import java.util.Collection;

import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ChessTeamArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ChessCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cchess.playerNotFound"));
    private static final SimpleCommandExceptionType ALREADY_IN_GAME_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cchess.alreadyInGame"));
    private static final SimpleCommandExceptionType NOT_IN_GAME_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cchess.notInGame"));

    public static ChessGame currentGame = null;
    public static String lastInvitedPlayer = null;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cchess")
            .executes(ctx -> openChessGame(ctx.getSource()))
            .then(literal("invite")
                .then(argument("opponent", gameProfile())
                    .executes(ctx -> invitePlayer(ctx.getSource(), getCProfileArgument(ctx, "opponent")))
                    .then(argument("team", chessTeam())
                        .executes(ctx -> invitePlayer(ctx.getSource(), getCProfileArgument(ctx, "opponent"), getChessTeam(ctx, "team")))))));
    }

    private static int openChessGame(FabricClientCommandSource source) throws CommandSyntaxException {
        if (currentGame == null) {
            throw NOT_IN_GAME_EXCEPTION.create();
        }
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        source.getClient().send(() -> source.getClient().setScreen(new ChessGameScreen(currentGame)));
        return Command.SINGLE_SUCCESS;
    }

    private static int invitePlayer(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        return invitePlayer(source, profiles, ChessTeam.WHITE);
    }

    private static int invitePlayer(FabricClientCommandSource source, Collection<GameProfile> profiles, ChessTeam chessSet) throws CommandSyntaxException {
        if (currentGame != null) {
            throw ALREADY_IN_GAME_EXCEPTION.create();
        }
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        assert source.getClient().getNetworkHandler() != null;
        PlayerListEntry recipient = source.getClient().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        ChessInviteC2CPacket packet = new ChessInviteC2CPacket(source.getClient().getNetworkHandler().getProfile().getName(), chessSet);
        lastInvitedPlayer = recipient.getProfile().getName();
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        Text text = Text.translatable("ccpacket.chessInviteC2CPacket.outgoing", recipient.getProfile().getName());
        source.sendFeedback(text);
        return Command.SINGLE_SUCCESS;
    }
}

class ChessGameScreen extends Screen {

    private final ChessGame game;
    private ChessPiece selectedPiece = null;

    private static final Identifier BOARD_TEXTURE = new Identifier("clientcommands:textures/chess/chess_board.png");
    private static final Identifier PIECES_TEXTURE = new Identifier("clientcommands:textures/chess/pieces.png");

    private static final int squareWidth = 32;
    private static final int squareHeight = 32;
    private static final int boardWidth = 8 * squareWidth;
    private static final int boardHeight = 8 * squareHeight;

    ChessGameScreen(ChessGame game) {
        super(Text.translatable("chessGame.title", game.getOpponent().getProfile().getName()));
        this.game = game;
    }

    @Override
    protected void init() {
        int startX = (this.width - boardWidth) / 2;
        int startY = (this.height - boardHeight) / 2 + boardHeight;
        int padding = 2;

        ButtonWidget resignButton = ButtonWidget.builder(Text.translatable("chessGame.resign"), button -> {
            try {
                ChessResignC2CPacket packet = new ChessResignC2CPacket();
                CCNetworkHandler.getInstance().sendPacket(packet, ChessCommand.currentGame.getOpponent());
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            ChessCommand.currentGame = null;
            this.close();
        })
                .dimensions(startX + padding, startY + padding, boardWidth / 2 - 2 * padding, 20)
                .build();
        this.addDrawableChild(resignButton);
        ButtonWidget shareButton = ButtonWidget.builder(Text.translatable("chessGame.exit"), button -> {
            this.close();
        })
                .dimensions(startX + boardWidth / 2 + padding, startY + padding, boardWidth / 2 - 2 * padding, 20)
                .build();
        this.addDrawableChild(shareButton);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(MatrixStack matrices) {
        super.renderBackground(matrices);
        int startX = (this.width - boardWidth) / 2;
        int startY = (this.height - boardHeight) / 2;

        drawTextWithShadow(matrices, client.textRenderer, this.title, startX, startY - 10, 0xff_ffffff);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, BOARD_TEXTURE);
        DrawableHelper.drawTexture(matrices, startX, startY, boardWidth, boardHeight, 0, 0, 2400, 2400, 2400, 2400);

        ChessBoard board = this.game.getBoard();
        RenderSystem.setShaderTexture(0, PIECES_TEXTURE);
        int padding = 2;
        int textureWidth = squareWidth - (2 * padding);
        int textureHeight = squareHeight - (2 * padding);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board.getPieceAt(x, 7 - y);
                if (piece == null) {
                    continue;
                }
                int squareStartX, squareStartY;
                if (this.game.getChessTeam() == ChessTeam.WHITE) {
                    squareStartX = startX + x * squareWidth;
                    squareStartY = startY + y * squareHeight;
                } else {
                    squareStartX = startX + (7 - x) * squareWidth;
                    squareStartY = startY + (7 - y) * squareHeight;
                }
                if (piece == this.selectedPiece) {
                    DrawableHelper.fill(matrices, squareStartX, squareStartY, squareStartX + squareWidth, squareStartY + squareHeight, 0xff_f21b42);
                }
                int textureStartX = squareStartX + padding;
                int textureStartY = squareStartY + padding;
                if (piece.team == ChessTeam.WHITE) {
                    DrawableHelper.drawTexture(matrices, textureStartX, textureStartY, textureWidth, textureHeight, piece.getTextureStart(), 0, 1024, 1024, 6 * 1024, 1024);
                    GL11C.glEnable(GL11C.GL_COLOR_LOGIC_OP);
                    GL11C.glLogicOp(GL11C.GL_INVERT);
                    DrawableHelper.drawTexture(matrices, textureStartX, textureStartY, textureWidth, textureHeight, piece.getTextureStart(), 0, 1024, 1024, 6 * 1024, 1024);
                    GL11C.glLogicOp(GL11C.GL_COPY);
                    GL11C.glDisable(GL11C.GL_COLOR_LOGIC_OP);
                } else {
                    DrawableHelper.drawTexture(matrices, textureStartX, textureStartY, textureWidth, textureHeight, piece.getTextureStart(), 0, 1024, 1024, 6 * 1024, 1024);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickedOnBoard(mouseX, mouseY)) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            int startX = (this.width - boardWidth) / 2;
            int startY = (this.height - boardHeight) / 2;
            int x, y;
            if (this.game.getChessTeam() == ChessTeam.WHITE) {
                x = (int) Math.round((mouseX - startX)) / squareWidth;
                y = 7 - (int) Math.round((mouseY - startY)) / squareHeight;
            } else {
                x = 7 - (int) Math.round((mouseX - startX)) / squareWidth;
                y = (int) Math.round((mouseY - startY)) / squareHeight;
            }
            if (this.selectedPiece == null) {
                ChessPiece piece = this.game.getBoard().getPieceAt(x, y);
                if (piece == null) {
                    this.selectedPiece = null;
                } else {
                    if (piece.team == this.game.getChessTeam()) {
                        this.selectedPiece = piece;
                    }
                }
            } else {
                Vector2i oldPosition = this.selectedPiece.getPosition();
                if (this.game.move(this.selectedPiece, new Vector2i(x, y))) {
                    try {
                        ChessBoardUpdateC2CPacket packet = new ChessBoardUpdateC2CPacket(oldPosition, new Vector2i(x, y));
                        CCNetworkHandler.getInstance().sendPacket(packet, this.game.getOpponent());
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                }
                this.selectedPiece = null;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickedOnBoard(double mouseX, double mouseY) {
        int startX = (this.width - boardWidth) / 2;
        int startY = (this.height - boardHeight) / 2;
        if (startX > mouseX || mouseX > startX + boardWidth) {
            return false;
        }
        if (startY > mouseY || mouseY > startY + boardHeight) {
            return false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // TODO: 22/12/2022 implement piece dragging, perhaps arrow drawing
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
}
