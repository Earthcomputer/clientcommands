package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.chess.ChessBoard;
import net.earthcomputer.clientcommands.c2c.chess.ChessGame;
import net.earthcomputer.clientcommands.c2c.chess.ChessPiece;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import net.earthcomputer.clientcommands.c2c.packets.*;
import net.earthcomputer.clientcommands.command.ChessCommand;
import net.earthcomputer.clientcommands.features.RunnableClickEventActionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector2i;
import org.slf4j.Logger;

import java.security.PublicKey;

public class CCNetworkHandler implements CCPacketListener {

    private static final SimpleCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.messageTooLong"));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.encryptionFailed"));

    private static final CCNetworkHandler instance = new CCNetworkHandler();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private CCNetworkHandler() {
    }

    public static CCNetworkHandler getInstance() {
        return instance;
    }

    public void sendPacket(C2CPacket packet, PlayerListEntry recipient) throws CommandSyntaxException {
        Integer id = CCPacketHandler.getId(packet.getClass());
        if (id == null) {
            LOGGER.warn("Could not send the packet because the id was not recognised");
            return;
        }
        PublicPlayerSession session = recipient.getSession();
        if (session == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PlayerPublicKey ppk = session.publicKeyData();
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        StringBuf buf = new StringBuf();
        buf.writeInt(id);
        packet.write(buf);
        byte[] compressed = ConversionHelper.Gzip.compress(buf.bytes());
        if (compressed.length > 245) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create();
        }
        byte[] encrypted = ConversionHelper.RsaEcb.encrypt(compressed, key);
        if (encrypted == null || encrypted.length == 0) {
            throw ENCRYPTION_FAILED_EXCEPTION.create();
        }
        String packetString = ConversionHelper.BaseUTF8.toUnicode(encrypted);
        String commandString = "w " + recipient.getProfile().getName() + " CCENC:" + packetString;
        if (commandString.length() >= 256) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create();
        }
        client.getNetworkHandler().sendChatCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.getSender();
        String message = packet.getMessage();
        MutableText prefix = Text.empty();
        prefix.append(Text.literal("[").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal("/cwe").formatted(Formatting.AQUA));
        prefix.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal(" "));
        Text text = prefix.append(Text.translatable("ccpacket.messageC2CPacket.incoming", sender, message).formatted(Formatting.GRAY));
        client.inGameHud.getChatHud().addMessage(text);
    }

    @Override
    public void onChessInviteC2CPacket(ChessInviteC2CPacket packet) {
        String sender = packet.getSender();
        PlayerListEntry player = client.getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(sender))
                .findFirst()
                .orElse(null);
        if (player == null) {
            return;
        }
        ChessTeam chessTeam = packet.getChessTeam();
        if (ChessCommand.currentGame != null) {
            try {
                ChessAcceptInviteC2CPacket acceptInvitePacket = new ChessAcceptInviteC2CPacket(client.getNetworkHandler().getProfile().getName(), false, chessTeam);
                CCNetworkHandler.getInstance().sendPacket(acceptInvitePacket, player);
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
                return;
            }
            client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.chessInviteC2CPacket.incoming.alreadyInGame", player.getProfile().getName()));
            return;
        }
        MutableText body = Text.translatable("ccpacket.chessInviteC2CPacket.incoming", sender, chessTeam.asString());
        Text accept = Text.translatable("ccpacket.chessInviteC2CPacket.incoming.accept").styled(style -> style
                .withFormatting(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, RunnableClickEventActionHelper.registerCode(() -> {
                    try {
                        ChessAcceptInviteC2CPacket acceptInvitePacket = new ChessAcceptInviteC2CPacket(client.getNetworkHandler().getProfile().getName(), true, chessTeam);
                        CCNetworkHandler.getInstance().sendPacket(acceptInvitePacket, player);
                        client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.chessAcceptInviteC2CPacket.outgoing.accept"));

                        ChessCommand.currentGame = new ChessGame(new ChessBoard(), player, chessTeam.other());
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                })))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("ccpacket.chessInviteC2CPacket.incoming.accept.hover"))));
        Text deny = Text.translatable("ccpacket.chessInviteC2CPacket.incoming.deny").styled(style -> style
                .withFormatting(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, RunnableClickEventActionHelper.registerCode(() -> {
                    try {
                        ChessAcceptInviteC2CPacket acceptInvitePacket = new ChessAcceptInviteC2CPacket(client.getNetworkHandler().getProfile().getName(), false, chessTeam);
                        CCNetworkHandler.getInstance().sendPacket(acceptInvitePacket, player);
                        client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.chessAcceptInviteC2CPacket.outgoing.deny"));
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                })))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("ccpacket.chessInviteC2CPacket.incoming.deny.hover"))));
        Text message = body.append(" ").append(accept).append("/").append(deny);
        client.inGameHud.getChatHud().addMessage(message);
    }

    @Override
    public void onChessAcceptInviteC2CPacket(ChessAcceptInviteC2CPacket packet) {
        if (ChessCommand.lastInvitedPlayer == null) {
            return;
        }
        String sender = packet.getSender();
        if (!sender.equals(ChessCommand.lastInvitedPlayer)) {
            return;
        }
        PlayerListEntry opponent = client.getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(sender))
                .findFirst()
                .orElse(null);
        if (opponent == null) {
            return;
        }
        boolean accept = packet.isAccept();
        Text text;
        if (accept) {
            text = Text.translatable("ccpacket.chessAcceptInviteC2CPacket.incoming.accept", sender);
            ChessCommand.currentGame = new ChessGame(new ChessBoard(), opponent, packet.getChessTeam());
        } else {
            text = Text.translatable("ccpacket.chessAcceptInviteC2CPacket.incoming.deny", sender);
        }
        client.inGameHud.getChatHud().addMessage(text);
    }

    @Override
    public void onChessBoardUpdateC2CPacket(ChessBoardUpdateC2CPacket packet) {
        if (ChessCommand.currentGame == null) {
            return;
        }
        int fromX = packet.getFromX();
        int fromY = packet.getFromY();
        int toX = packet.getToX();
        int toY = packet.getToY();
        ChessPiece piece = ChessCommand.currentGame.getBoard().getPieceAt(fromX, fromY);
        String sender = ChessCommand.currentGame.getOpponent().getProfile().getName();
        Text text;
        if (ChessCommand.currentGame.move(piece, new Vector2i(toX, toY))) {
            //noinspection ConstantConditions
            text = Text.translatable("ccpacket.chessBoardUpdateC2CPacket.incoming", sender, piece.getName(), ChessBoard.indexToFile(toX), toY + 1);
        } else {
            text = Text.translatable("ccpacket.chessBoardUpdateC2CPacket.incoming.invalid", sender);
        }
        client.inGameHud.getChatHud().addMessage(text);
    }

    @Override
    public void onChessResignC2CPacket(ChessResignC2CPacket packet) {
        if (ChessCommand.currentGame == null) {
            return;
        }
        String sender = ChessCommand.currentGame.getOpponent().getProfile().getName();
        Text text = Text.translatable("ccpacket.chessResignC2CPacket.incoming", sender);
        ChessCommand.currentGame = null;
        client.inGameHud.getChatHud().addMessage(text);
    }
}
