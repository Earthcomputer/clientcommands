package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;

import java.util.ArrayList;
import java.util.Collection;

public record SnakeAddPlayersC2CPacket(Collection<String> players) implements C2CPacket {
    public SnakeAddPlayersC2CPacket(StringBuf buf) {
        this((Collection<String>)buf.readCollection(ArrayList::new, StringBuf::readString));
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeCollection(players, StringBuf::writeString);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onSnakeAddPlayersC2CPacket(this);
    }
}
