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

public record MessageC2CPacket(@Nullable String sender, String message) implements C2CPacket {
    public static final StreamCodec<C2CFriendlyByteBuf, MessageC2CPacket> CODEC = Packet.codec(MessageC2CPacket::write, MessageC2CPacket::new);
    public static final PacketType<MessageC2CPacket> ID = new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("clientcommands", "message"));

    public MessageC2CPacket(C2CFriendlyByteBuf buf) {
        this(buf.getSender(), buf.readUtf());
    }

    public void write(C2CFriendlyByteBuf buf) {
        buf.writeUtf(this.message);
    }

    @Override
    public void handle(C2CPacketListener handler) {
        handler.onMessageC2CPacket(this);
    }

    @Override
    public PacketType<? extends Packet<C2CPacketListener>> type() {
        return ID;
    }
}
