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

public record PutTicTacToeMarkC2CPacket(String sender, byte x, byte y) implements C2CPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, PutTicTacToeMarkC2CPacket> CODEC = Packet.codec(PutTicTacToeMarkC2CPacket::write, PutTicTacToeMarkC2CPacket::new);
    public static final PacketType<PutTicTacToeMarkC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.fromNamespaceAndPath("clientcommands", "put_tic_tac_toe_mark"));

    public PutTicTacToeMarkC2CPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readByte(), buf.readByte());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.sender);
        buf.writeByte(this.x);
        buf.writeByte(this.y);
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onPutTicTacToeMarkC2CPacket(this);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }
}
