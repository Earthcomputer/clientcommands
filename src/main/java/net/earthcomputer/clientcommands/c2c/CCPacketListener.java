package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.packets.DiceRollC2CPackets;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;

public interface CCPacketListener {
    void onMessageC2CPacket(MessageC2CPacket packet);

    void onCoinflipInitC2CPacket(DiceRollC2CPackets.DiceRollInitC2CPacket packet) throws CommandSyntaxException;

    void onCoinflipAcceptedC2CPacket(DiceRollC2CPackets.DiceRollAcceptedC2CPacket packet) throws CommandSyntaxException;

    void onCoinflipResultC2CPacket(DiceRollC2CPackets.DiceRollResultC2CPacket packet);
}
