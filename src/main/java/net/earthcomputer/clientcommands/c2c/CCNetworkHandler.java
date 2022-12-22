package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.packets.*;
import net.earthcomputer.clientcommands.command.SnakeCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

public class CCNetworkHandler implements CCPacketListener {

    private static final SimpleCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.messageTooLong"));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.encryptionFailed"));

    private static final CCNetworkHandler instance = new CCNetworkHandler();

    private static final Logger LOGGER = LogUtils.getLogger();

    private CCNetworkHandler() {
    }

    public static CCNetworkHandler getInstance() {
        return instance;
    }

    public static Optional<PlayerListEntry> getPlayerByName(String name) {
        assert MinecraftClient.getInstance().getNetworkHandler() != null;
        return MinecraftClient.getInstance()
            .getNetworkHandler()
            .getPlayerList()
            .stream()
            .filter(p -> p.getProfile().getName().equalsIgnoreCase(name))
            .findFirst();
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
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(commandString);
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
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
    }

    @Override
    public void onSnakeInviteC2CPacket(SnakeInviteC2CPacket packet) {
        // TODO: Use Text.translatable
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
            Text.literal(packet.sender())
                .append(" invited you to a game of snake. ")
                .append(
                    Text.literal("[Join]")
                        .styled(style ->
                            style.withColor(Formatting.GREEN)
                                .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT, Text.literal("Join game")
                                ))
                                .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND, "/csnake join " + packet.sender()
                                ))
                        )
                )
                .styled(style -> style.withColor(Formatting.GRAY))
        );
    }

    @Override
    public void onSnakeJoinC2CPacket(SnakeJoinC2CPacket packet) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof SnakeCommand.SnakeGameScreen snakeScreen)) return;
        final SnakeAddPlayersC2CPacket addPlayersPacket = new SnakeAddPlayersC2CPacket(List.of(packet.sender()));
        for (final String otherPlayer : snakeScreen.getOtherSnakes().keySet()) {
            getPlayerByName(otherPlayer).ifPresent(actualPlayer -> {
                try {
                    sendPacket(addPlayersPacket, actualPlayer);
                } catch (CommandSyntaxException e) {
                    LOGGER.warn("Failed to recast snake player join", e);
                }
            });
        }
        getPlayerByName(packet.sender()).ifPresent(sender -> {
            try {
                sendPacket(new SnakeAddPlayersC2CPacket(snakeScreen.getOtherSnakes().keySet()), sender);
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Failed to reply to snake player join", e);
            }
        });
        snakeScreen.getOtherSnakes().put(packet.sender(), List.of()); // Reserve entry in player list
    }

    @Override
    public void onSnakeAddPlayersC2CPacket(SnakeAddPlayersC2CPacket packet) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof SnakeCommand.SnakeGameScreen snakeScreen)) return;
        for (final String player : packet.players()) {
            snakeScreen.getOtherSnakes().put(player, List.of());
        }
    }

    @Override
    public void onSnakeBodyC2CPacket(SnakeBodyC2CPacket packet) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof SnakeCommand.SnakeGameScreen snakeScreen)) return;
        snakeScreen.getOtherSnakes().put(packet.sender(), packet.segments());
    }

    @Override
    public void onSnakeRemovePlayerC2CPacket(SnakeRemovePlayerC2CPacket packet) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof SnakeCommand.SnakeGameScreen snakeScreen)) return;
        snakeScreen.getOtherSnakes().remove(packet.player());
    }
}
