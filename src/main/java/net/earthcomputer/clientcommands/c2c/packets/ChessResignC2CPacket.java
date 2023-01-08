package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.minecraft.network.PacketByteBuf;


public class ChessResignC2CPacket implements C2CPacket {

    public ChessResignC2CPacket() {
    }

    public ChessResignC2CPacket(PacketByteBuf raw) {
    }

    @Override
    public void write(PacketByteBuf buf) {
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onChessResignC2CPacket(this);
    }
}
