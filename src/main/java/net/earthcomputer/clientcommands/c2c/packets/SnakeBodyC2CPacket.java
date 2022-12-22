package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import net.earthcomputer.clientcommands.command.SnakeCommand;

import java.util.ArrayList;
import java.util.List;

public record SnakeBodyC2CPacket(String sender, List<SnakeCommand.Vec2i> segments) implements C2CPacket {
    public SnakeBodyC2CPacket(StringBuf buf) {
        this(buf.readString(), buf.readCollection(ArrayList::new, SnakeCommand.Vec2i::new));
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeString(sender);
        buf.writeCollection(segments, SnakeCommand.Vec2i::write);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onSnakeBodyC2CPacket(this);
    }
}
