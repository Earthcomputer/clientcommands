package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import net.earthcomputer.clientcommands.command.SnakeCommand;

public record SnakeSyncAppleC2CPacket(SnakeCommand.Vec2i applePos) implements C2CPacket {
    public SnakeSyncAppleC2CPacket(StringBuf buf) {
        this(new SnakeCommand.Vec2i(buf));
    }

    @Override
    public void write(StringBuf buf) {
        SnakeCommand.Vec2i.write(buf, applePos);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onSnakeSyncAppleC2CPacket(this);
    }
}
