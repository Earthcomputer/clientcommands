package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.earthcomputer.clientcommands.features.TwoPlayerGameType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public record StartTwoPlayerGameC2CPacket(String sender, boolean accept, TwoPlayerGameType<?> game) implements C2CPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTwoPlayerGameC2CPacket> CODEC = Packet.codec(StartTwoPlayerGameC2CPacket::write, StartTwoPlayerGameC2CPacket::new);
    public static final PacketType<StartTwoPlayerGameC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.fromNamespaceAndPath("clientcommands", "start_two_player_game"));

    public StartTwoPlayerGameC2CPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readBoolean(), TwoPlayerGameType.getById(buf.readResourceLocation()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.sender);
        buf.writeBoolean(this.accept);
        buf.writeResourceLocation(this.game.getId());
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onStartTwoPlayerGameC2CPacket(this);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }
}
