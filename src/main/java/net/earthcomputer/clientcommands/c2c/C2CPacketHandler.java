package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTicTacToeGameC2CPacket;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.earthcomputer.clientcommands.command.TicTacToeCommand;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.security.PublicKey;

public class C2CPacketHandler implements C2CPacketListener {

    private static final DynamicCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new DynamicCommandExceptionType(d -> Component.translatable("c2cpacket.messageTooLong", d));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.encryptionFailed"));

    public static final ProtocolInfo<C2CPacketListener> C2C = ProtocolInfoBuilder.<C2CPacketListener, RegistryFriendlyByteBuf>protocolUnbound(ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND, builder -> builder
        .addPacket(MessageC2CPacket.ID, MessageC2CPacket.CODEC)
        .addPacket(StartTicTacToeGameC2CPacket.ID, StartTicTacToeGameC2CPacket.CODEC)
        .addPacket(PutTicTacToeMarkC2CPacket.ID, PutTicTacToeMarkC2CPacket.CODEC)
    ).bind(RegistryFriendlyByteBuf.decorator(Minecraft.getInstance().getConnection().registryAccess()));

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
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        FriendlyByteBuf buf = PacketByteBufs.create();
        C2C.codec().encode(buf, packet);
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
        ListenCommand.onPacket(packet, ListenCommand.PacketFlow.C2C_OUTBOUND);
        Minecraft.getInstance().getConnection().sendCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
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

    @Override
    public PacketFlow flow() {
        return C2C.flow();
    }

    @Override
    public ConnectionProtocol protocol() {
        return C2C.id();
    }

    @Override
    public void onDisconnect(Component reason) {
    }

    @Override
    public boolean isAcceptingMessages() {
        return true;
    }
}
