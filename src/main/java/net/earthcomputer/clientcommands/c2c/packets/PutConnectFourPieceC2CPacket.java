package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public record PutConnectFourPieceC2CPacket(String sender, int x) implements C2CPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, PutConnectFourPieceC2CPacket> CODEC = Packet.codec(PutConnectFourPieceC2CPacket::write, PutConnectFourPieceC2CPacket::new);
    public static final PacketType<PutConnectFourPieceC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.fromNamespaceAndPath("clientcommands", "put_connect_four_piece"));

    public PutConnectFourPieceC2CPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sender);
        buf.writeVarInt(x);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onPutConnectFourPieceC2CPacket(this);
    }
}
