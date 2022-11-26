package net.earthcomputer.clientcommands.c2c;

public interface C2CPacket {
    void write(StringBuf buf);

    void apply(CCPacketListener listener);
}
