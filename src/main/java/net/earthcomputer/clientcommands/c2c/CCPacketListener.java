package net.earthcomputer.clientcommands.c2c;

import net.earthcomputer.clientcommands.c2c.packets.*;

public interface CCPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onSnakeInviteC2CPacket(SnakeInviteC2CPacket packet);

    void onSnakeJoinC2CPacket(SnakeJoinC2CPacket packet);

    void onSnakeAddPlayersC2CPacket(SnakeAddPlayersC2CPacket packet);

    void onSnakeBodyC2CPacket(SnakeBodyC2CPacket packet);

    void onSnakeRemovePlayerC2CPacket(SnakeRemovePlayerC2CPacket packet);
}
