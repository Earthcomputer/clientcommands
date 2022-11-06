package net.earthcomputer.clientcommands.c2c;

public interface CCPacket {
    void write(StringBuf buf);

    void apply(CCPacketListener listener);
}
