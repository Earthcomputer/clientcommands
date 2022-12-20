package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.c2c.packets.CoinflipC2CPackets;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.command.CoinflipCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.security.PublicKey;

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
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(id);
        packet.write(buf);
        byte[] compressed = ConversionHelper.Gzip.compress(buf.getWrittenBytes());
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
    public void onCoinflipInitC2CPacket(CoinflipC2CPackets.CoinflipInitC2CPacket packet) throws CommandSyntaxException {
        CoinflipCommand.initCoinflip(packet);
    }

    @Override
    public void onCoinflipAcceptedC2CPacket(CoinflipC2CPackets.CoinflipAcceptedC2CPacket packet) throws CommandSyntaxException {
        CoinflipCommand.acceptCoinflip(packet);
    }

    @Override
    public void onCoinflipResultC2CPacket(CoinflipC2CPackets.CoinflipResultC2CPacket packet) {
        CoinflipCommand.completeCoinflip(packet);
    }
}
