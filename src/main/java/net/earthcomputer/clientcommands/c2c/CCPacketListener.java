package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.packets.CoinflipC2CPackets;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;

public interface CCPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onCoinflipInitC2CPacket(CoinflipC2CPackets.CoinflipInitC2CPacket packet) throws CommandSyntaxException;

    void onCoinflipResultC2CPacket(CoinflipC2CPackets.CoinflipResultC2CPacket packet);
}
