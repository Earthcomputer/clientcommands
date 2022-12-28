package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;

public interface CCPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);
}
