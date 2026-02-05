package net.earthcomputer.clientcommands.c2c.packets;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CFriendlyByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.earthcomputer.clientcommands.c2c.chess.ChessMove;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.IntFunction;

public record ChessDrawOfferC2CPacket(@Nullable String sender, @Nullable UUID senderUUID, Operation operation) implements C2CPacket {
    public static final StreamCodec<C2CFriendlyByteBuf, ChessDrawOfferC2CPacket> CODEC = Packet.codec(ChessDrawOfferC2CPacket::write, ChessDrawOfferC2CPacket::new);
    public static final PacketType<ChessDrawOfferC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("clientcommands", "draw_offer"));

    private ChessDrawOfferC2CPacket(C2CFriendlyByteBuf buf) {
        this(buf.getSender(), buf.getSenderUUID(), Operation.STREAM_CODEC.decode(buf));
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }

    private void write(C2CFriendlyByteBuf buf) {
        Operation.STREAM_CODEC.encode(buf, operation);
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onChessDrawOfferPacket(this);
    }

    public enum Operation {
        OFFER, RETRACT, ACCEPT;

        private static final IntFunction<Operation> BY_ID = ByIdMap.continuous(Operation::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Operation> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Operation::ordinal);
    }
}
