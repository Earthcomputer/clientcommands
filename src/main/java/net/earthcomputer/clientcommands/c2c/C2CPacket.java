package net.earthcomputer.clientcommands.c2c;

import net.minecraft.network.protocol.Packet;

public interface C2CPacket extends Packet<C2CPacketListener> {
    String sender();
}
