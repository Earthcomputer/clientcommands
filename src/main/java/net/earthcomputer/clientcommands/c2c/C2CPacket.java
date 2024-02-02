package net.earthcomputer.clientcommands.c2c;

import net.minecraft.network.FriendlyByteBuf;

public interface C2CPacket {
    void write(FriendlyByteBuf buf);

    void apply(CCPacketListener listener);
}
