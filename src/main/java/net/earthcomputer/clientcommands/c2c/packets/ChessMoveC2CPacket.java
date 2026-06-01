package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CFriendlyByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.earthcomputer.clientcommands.c2c.chess.ChessMove;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ChessMoveC2CPacket(@Nullable String sender, @Nullable UUID senderUUID, ChessMove move) implements C2CPacket {
    public static final StreamCodec<C2CFriendlyByteBuf, ChessMoveC2CPacket> CODEC = Packet.codec(ChessMoveC2CPacket::write, ChessMoveC2CPacket::new);
    public static final PacketType<ChessMoveC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("clientcommands", "chess_move"));

    private ChessMoveC2CPacket(C2CFriendlyByteBuf buf) {
        this(buf.getSender(), buf.getSenderUUID(), ChessMove.STREAM_CODEC.decode(buf));
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }

    private void write(C2CFriendlyByteBuf buf) {
        ChessMove.STREAM_CODEC.encode(buf, move);
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onChessMovePacket(this);
    }
}
