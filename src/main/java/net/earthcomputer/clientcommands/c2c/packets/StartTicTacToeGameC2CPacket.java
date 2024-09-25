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

public record StartTicTacToeGameC2CPacket(String sender, boolean accept) implements C2CPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTicTacToeGameC2CPacket> CODEC = Packet.codec(StartTicTacToeGameC2CPacket::write, StartTicTacToeGameC2CPacket::new);
    public static final PacketType<StartTicTacToeGameC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.fromNamespaceAndPath("clientcommands", "start_tic_tac_toe_game"));

    public StartTicTacToeGameC2CPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.sender);
        buf.writeBoolean(this.accept);
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onStartTicTacToeGameC2CPacket(this);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }
}
