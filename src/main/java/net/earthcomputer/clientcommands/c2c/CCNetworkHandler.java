package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.kinds.IdF;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.security.PublicKey;

public class CCNetworkHandler implements CCPacketListener {

    private static final SimpleCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.messageTooLong"));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("ccpacket.encryptionFailed"));

    public static boolean justSent = false;

    private static CCNetworkHandler instance;

    private CCNetworkHandler() {
    }

    public static CCNetworkHandler getInstance() {
        if (instance == null) {
            instance = new CCNetworkHandler();
        }
        return instance;
    }

    public void sendPacket(CCPacket packet, PlayerListEntry recipient) throws CommandSyntaxException {
        Integer id = CCPacketHandler.getId(packet.getClass());
        if (id == null) {
            return;
        }
        PlayerPublicKey ppk = recipient.getPublicKeyData();
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
        String commandString = "w " + recipient.getProfile().getName() + " CCENC:" + ConversionHelper.BaseUTF8.toUnicode(encrypted);
        if (commandString.length() >= 256) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create();
        }
        MinecraftClient.getInstance().player.sendCommand(commandString, null);
        justSent = true;
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.getSender();
        String message = packet.getMessage();
        MutableText prefix = Text.empty();
        prefix.append(Text.literal("[").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal("/cwe").formatted(Formatting.AQUA));
        prefix.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        Text text = prefix.append(" " + sender + " -> you: " + message).formatted(Formatting.GRAY);
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
    }
}
