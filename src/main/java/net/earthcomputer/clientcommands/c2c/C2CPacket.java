package net.earthcomputer.clientcommands.c2c;

import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;

public interface C2CPacket extends Packet<C2CPacketListener> {
    @Nullable
    String sender();
}
