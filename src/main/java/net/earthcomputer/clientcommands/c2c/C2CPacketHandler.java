package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTicTacToeGameC2CPacket;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.earthcomputer.clientcommands.interfaces.IClientPacketListener_C2C;
import net.earthcomputer.clientcommands.command.TicTacToeCommand;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Optional;

public class C2CPacketHandler implements C2CPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new DynamicCommandExceptionType(d -> Component.translatable("c2cpacket.messageTooLong", d));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.encryptionFailed"));

    public static final ProtocolInfo.Unbound<C2CPacketListener, RegistryFriendlyByteBuf> PROTOCOL_UNBOUND = ProtocolInfoBuilder.protocolUnbound(ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND, builder -> builder
        .addPacket(MessageC2CPacket.ID, MessageC2CPacket.CODEC)
        .addPacket(StartTicTacToeGameC2CPacket.ID, StartTicTacToeGameC2CPacket.CODEC)
        .addPacket(PutTicTacToeMarkC2CPacket.ID, PutTicTacToeMarkC2CPacket.CODEC)
    );

    public static final String C2C_PACKET_HEADER = "CCΕNC:";

    private static final C2CPacketHandler instance = new C2CPacketHandler();

    private C2CPacketHandler() {
    }

    public static C2CPacketHandler getInstance() {
        return instance;
    }

    public void sendPacket(Packet<C2CPacketListener> packet, PlayerInfo recipient) throws CommandSyntaxException {
        RemoteChatSession session = recipient.getChatSession();
        if (session == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        ProfilePublicKey ppk = session.profilePublicKey();
        //noinspection ConstantValue
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        FriendlyByteBuf buf = PacketByteBufs.create();
        ProtocolInfo<C2CPacketListener> protocolInfo = getCurrentProtocolInfo();
        if (protocolInfo == null) {
            return;
        }
        protocolInfo.codec().encode(buf, packet);
        byte[] uncompressed = new byte[buf.readableBytes()];
        buf.getBytes(0, uncompressed);
        byte[] compressed = ConversionHelper.Gzip.compress(uncompressed);
        if (compressed == null) {
            return;
        }
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
        String commandString = "w " + recipient.getProfile().getName() + ' ' + C2C_PACKET_HEADER + packetString;
        if (commandString.length() >= SharedConstants.MAX_CHAT_LENGTH) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(commandString.length());
        }
        ListenCommand.onPacket(packet, ListenCommand.PacketFlow.C2C_OUTBOUND);
        Minecraft.getInstance().getConnection().sendCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
    }

    public static boolean handleC2CPacket(String content, String sender) {
        byte[] encrypted = ConversionHelper.BaseUTF8.fromUnicode(content);
        // round down to multiple of 256 bytes
        int length = encrypted.length & ~0xFF;
        // copy to new array of arrays
        byte[][] encryptedArrays = new byte[length / 256][];
        for (int i = 0; i < length; i += 256) {
            encryptedArrays[i / 256] = Arrays.copyOfRange(encrypted, i, i + 256);
        }
        if (!(Minecraft.getInstance().getProfileKeyPairManager() instanceof AccountProfileKeyPairManager profileKeyPairManager)) {
            return false;
        }
        Optional<ProfileKeyPair> keyPair = profileKeyPairManager.keyPair.join();
        if (keyPair.isEmpty()) {
            return false;
        }
        // decrypt
        int len = 0;
        byte[][] decryptedArrays = new byte[encryptedArrays.length][];
        for (int i = 0; i < encryptedArrays.length; i++) {
            decryptedArrays[i] = ConversionHelper.RsaEcb.decrypt(encryptedArrays[i], keyPair.get().privateKey());
            if (decryptedArrays[i] == null) {
                return false;
            }
            len += decryptedArrays[i].length;
        }
        // copy to new array
        byte[] decrypted = new byte[len];
        int pos = 0;
        for (byte[] decryptedArray : decryptedArrays) {
            System.arraycopy(decryptedArray, 0, decrypted, pos, decryptedArray.length);
            pos += decryptedArray.length;
        }
        byte[] uncompressed = ConversionHelper.Gzip.decompress(decrypted);
        if (uncompressed == null) {
            return false;
        }
        ProtocolInfo<C2CPacketListener> protocolInfo = getCurrentProtocolInfo();
        if (protocolInfo == null) {
            return false;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed));
        C2CPacket packet;
        try {
            packet = (C2CPacket) protocolInfo.codec().decode(buf);
        } catch (Throwable e) {
            LOGGER.error("Error decoding C2C packet", e);
            return false;
        }
        if (buf.readableBytes() > 0) {
            LOGGER.error("Found extra bytes while reading C2C packet {}", packet.type());
            return false;
        }
        if (!packet.sender().equals(sender)) {
            LOGGER.error("Detected mismatching packet sender. Expected {}, got {}", sender, packet.sender());
            return false;
        }
        ListenCommand.onPacket(packet, ListenCommand.PacketFlow.C2C_INBOUND);
        try {
            packet.handle(C2CPacketHandler.getInstance());
        } catch (Throwable e) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.nullToEmpty(e.getMessage()));
            LOGGER.error("Error handling C2C packet", e);
        }
        return true;
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.sender();
        String message = packet.message();
        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal("/cwe").withStyle(ChatFormatting.AQUA));
        prefix.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal(" "));
        Component component = prefix.append(Component.translatable("c2cpacket.messageC2CPacket.incoming", sender, message).withStyle(ChatFormatting.GRAY));
        Minecraft.getInstance().gui.getChat().addMessage(component);
    }

    @Override
    public void onStartTicTacToeGameC2CPacket(StartTicTacToeGameC2CPacket packet) {
        TicTacToeCommand.onStartTicTacToeGameC2CPacket(packet);
    }

    @Override
    public void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet) {
        TicTacToeCommand.onPutTicTacToeMarkC2CPacket(packet);
    }

    @Nullable
    public static ProtocolInfo<C2CPacketListener> getCurrentProtocolInfo() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        return ((IClientPacketListener_C2C) connection).clientcommands_getC2CProtocolInfo();
    }

    @Override
    public @NotNull PacketFlow flow() {
        return PacketFlow.CLIENTBOUND;
    }

    @Override
    public @NotNull ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    @Override
    public void onDisconnect(Component reason) {
    }

    @Override
    public boolean isAcceptingMessages() {
        return true;
    }
}
