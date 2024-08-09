package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutConnectFourPieceC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTwoPlayerGameC2CPacket;
import net.minecraft.network.ClientboundPacketListener;

public interface C2CPacketListener extends ClientboundPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onStartTwoPlayerGameC2CPacket(StartTwoPlayerGameC2CPacket packet);

    void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet);

    void onPutConnectFourPieceC2CPacket(PutConnectFourPieceC2CPacket packet);
}
