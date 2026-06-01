package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CFriendlyByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ChessResignC2CPacket(@Nullable String sender, @Nullable UUID senderUUID) implements C2CPacket {
    public static final StreamCodec<C2CFriendlyByteBuf, ChessResignC2CPacket> CODEC = Packet.codec((packet, buf) -> {}, ChessResignC2CPacket::new);
    public static final PacketType<ChessResignC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("clientcommands", "chess_resign"));

    private ChessResignC2CPacket(C2CFriendlyByteBuf buf) {
        this(buf.getSender(), buf.getSenderUUID());
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onChessResignPacket(this);
    }
}
