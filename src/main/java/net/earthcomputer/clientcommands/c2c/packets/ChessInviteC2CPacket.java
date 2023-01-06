package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;

import java.util.Locale;

public class ChessInviteC2CPacket implements C2CPacket {

    private final String sender;
    private final ChessTeam chessTeam;

    public ChessInviteC2CPacket(String sender, ChessTeam set) {
        this.sender = sender;
        this.chessTeam = set;
    }

    public ChessInviteC2CPacket(StringBuf raw) {
        this.sender = raw.readString();
        this.chessTeam = ChessTeam.valueOf(raw.readString().toUpperCase(Locale.ROOT));
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeString(this.sender);
        buf.writeString(this.chessTeam.asString());
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onChessInviteC2CPacket(this);
    }

    public String getSender() {
        return this.sender;
    }

    public ChessTeam getChessTeam() {
        return this.chessTeam;
    }
}
