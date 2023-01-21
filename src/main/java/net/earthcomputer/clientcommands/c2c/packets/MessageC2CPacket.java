package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.minecraft.network.PacketByteBuf;

public class MessageC2CPacket implements C2CPacket {

    private final String sender;
    private final String message;

    public MessageC2CPacket(String sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    public MessageC2CPacket(PacketByteBuf raw) {
        this.sender = raw.readString();
        this.message = raw.readString();
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(this.sender);
        buf.writeString(this.message);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onMessageC2CPacket(this);
    }

    public String getSender() {
        return this.sender;
    }

    public String getMessage() {
        return this.message;
    }
}
