package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;

public record SnakeInviteC2CPacket(String sender) implements C2CPacket {
    public SnakeInviteC2CPacket(StringBuf buf) {
        this(buf.readString());
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeString(sender);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onSnakeInviteC2CPacket(this);
    }
}
