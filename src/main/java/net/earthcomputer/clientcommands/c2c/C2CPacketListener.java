package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTicTacToeGameC2CPacket;
import net.minecraft.network.ClientboundPacketListener;

public interface C2CPacketListener extends ClientboundPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onStartTicTacToeGameC2CPacket(StartTicTacToeGameC2CPacket packet);

    void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet);
}
