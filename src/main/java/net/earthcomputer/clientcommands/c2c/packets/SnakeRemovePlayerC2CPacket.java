package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;

public record SnakeRemovePlayerC2CPacket(String player) implements C2CPacket {
    public SnakeRemovePlayerC2CPacket(StringBuf buf) {
        this(buf.readString());
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeString(player);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onSnakeRemovePlayerC2CPacket(this);
    }
}
