package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.*;

public interface CCPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onChessInviteC2CPacket(ChessInviteC2CPacket packet);

    void onChessAcceptInviteC2CPacket(ChessAcceptInviteC2CPacket packet);

    void onChessBoardUpdateC2CPacket(ChessBoardUpdateC2CPacket packet);

    void onChessResignC2CPacket(ChessResignC2CPacket packet);
}
