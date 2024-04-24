package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.minecraft.network.PacketListener;

public interface C2CPacketListener extends PacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);
}
