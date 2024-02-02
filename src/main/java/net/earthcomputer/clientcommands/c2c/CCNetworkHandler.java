package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.slf4j.Logger;

import java.security.PublicKey;

public class CCNetworkHandler implements CCPacketListener {

    private static final DynamicCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new DynamicCommandExceptionType(d -> Component.translatable("ccpacket.messageTooLong", d));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("ccpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("ccpacket.encryptionFailed"));

    private static final CCNetworkHandler instance = new CCNetworkHandler();

    private static final Logger LOGGER = LogUtils.getLogger();

    private CCNetworkHandler() {
    }

    public static CCNetworkHandler getInstance() {
        return instance;
    }

    public void sendPacket(C2CPacket packet, PlayerInfo recipient) throws CommandSyntaxException {
        Integer id = CCPacketHandler.getId(packet.getClass());
        if (id == null) {
            LOGGER.warn("Could not send the packet because the id was not recognised");
            return;
        }
        RemoteChatSession session = recipient.getChatSession();
        if (session == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        ProfilePublicKey ppk = session.profilePublicKey();
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(id);
        packet.write(buf);
        byte[] uncompressed = new byte[buf.readableBytes()];
        buf.getBytes(0, uncompressed);
        byte[] compressed = ConversionHelper.Gzip.compress(uncompressed);
        // split compressed into 245 byte chunks
        int chunks = (compressed.length + 244) / 245;
        byte[][] chunked = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            int start = i * 245;
            int end = Math.min(start + 245, compressed.length);
            chunked[i] = new byte[end - start];
            System.arraycopy(compressed, start, chunked[i], 0, end - start);
        }
        // encrypt each chunk
        byte[][] encrypted = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            encrypted[i] = ConversionHelper.RsaEcb.encrypt(chunked[i], key);
            if (encrypted[i] == null || encrypted[i].length == 0) {
                throw ENCRYPTION_FAILED_EXCEPTION.create();
            }
        }
        // join encrypted chunks into one byte array
        byte[] joined = new byte[encrypted.length * 256];
        for (int i = 0; i < encrypted.length; i++) {
            System.arraycopy(encrypted[i], 0, joined, i * 256, 256);
        }
        String packetString = ConversionHelper.BaseUTF8.toUnicode(joined);
        String commandString = "w " + recipient.getProfile().getName() + " CCENC:" + packetString;
        if (commandString.length() >= 256) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(commandString.length());
        }
        Minecraft.getInstance().getConnection().sendCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.getSender();
        String message = packet.getMessage();
        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal("/cwe").withStyle(ChatFormatting.AQUA));
        prefix.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal(" "));
        Component text = prefix.append(Component.translatable("ccpacket.messageC2CPacket.incoming", sender, message).withStyle(ChatFormatting.GRAY));
        Minecraft.getInstance().gui.getChat().addMessage(text);
    }
}
