package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import org.joml.Vector2i;

public class ChessBoardUpdateC2CPacket implements C2CPacket {

    private final int fromX;
    private final int fromY;
    private final int toX;
    private final int toY;

    public ChessBoardUpdateC2CPacket(Vector2i from, Vector2i to) {
        this.fromX = from.x;
        this.fromY = from.y;
        this.toX = to.x;
        this.toY = to.y;
    }

    public ChessBoardUpdateC2CPacket(StringBuf raw) {
        this.fromX = raw.readInt();
        this.fromY = raw.readInt();
        this.toX = raw.readInt();
        this.toY = raw.readInt();
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeInt(this.fromX);
        buf.writeInt(this.fromY);
        buf.writeInt(this.toX);
        buf.writeInt(this.toY);
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onChessBoardUpdateC2CPacket(this);
    }

    public int getFromX() {
        return this.fromX;
    }

    public int getFromY() {
        return this.fromY;
    }

    public int getToX() {
        return this.toX;
    }

    public int getToY() {
        return this.toY;
    }
}
