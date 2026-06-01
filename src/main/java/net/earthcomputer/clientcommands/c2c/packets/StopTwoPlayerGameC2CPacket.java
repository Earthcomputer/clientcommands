package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CFriendlyByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record StopTwoPlayerGameC2CPacket(@Nullable String sender, @Nullable UUID senderUUID, TwoPlayerGame<?, ?> game) implements C2CPacket {
    public static final StreamCodec<C2CFriendlyByteBuf, StopTwoPlayerGameC2CPacket> CODEC = Packet.codec(StopTwoPlayerGameC2CPacket::write, StopTwoPlayerGameC2CPacket::new);
    public static final PacketType<StopTwoPlayerGameC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("clientcommands", "stop_two_player_game"));

    private StopTwoPlayerGameC2CPacket(C2CFriendlyByteBuf buf) {
        this(buf.getSender(), buf.getSenderUUID(), TwoPlayerGame.getByIdOrThrow(buf.readIdentifier()));
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }

    public void write(C2CFriendlyByteBuf buf) {
        buf.writeIdentifier(game.getId());
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onStopTwoPlayerGameC2CPacket(this);
    }
}
