package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;

import java.util.Locale;

public class ChessAcceptInviteC2CPacket implements C2CPacket {

    private final String sender;
    private final boolean accept;
    private final ChessTeam chessTeam;

    public ChessAcceptInviteC2CPacket(String sender, boolean accept, ChessTeam chessTeam) {
        this.sender = sender;
        this.accept = accept;
        this.chessTeam = chessTeam;
    }

    public ChessAcceptInviteC2CPacket(StringBuf raw) {
        this.sender = raw.readString();
        this.accept = Boolean.parseBoolean(raw.readString());
        this.chessTeam = ChessTeam.valueOf(raw.readString().toUpperCase(Locale.ROOT));
    }

    @Override
    public void write(StringBuf buf) {
        buf.writeString(this.sender);
        buf.writeString(Boolean.toString(this.accept));
        buf.writeString(this.chessTeam.asString());
    }

    @Override
    public void apply(CCPacketListener listener) {
        listener.onChessAcceptInviteC2CPacket(this);
    }

    public String getSender() {
        return this.sender;
    }

    public boolean isAccept() {
        return this.accept;
    }

    public ChessTeam getChessTeam() {
        return this.chessTeam;
    }
}
