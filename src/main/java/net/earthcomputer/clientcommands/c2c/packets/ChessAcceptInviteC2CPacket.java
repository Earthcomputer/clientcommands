package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import net.minecraft.network.PacketByteBuf;

public class ChessAcceptInviteC2CPacket implements C2CPacket {

    private final String sender;
    private final boolean accept;
    private final ChessTeam chessTeam;

    public ChessAcceptInviteC2CPacket(String sender, boolean accept, ChessTeam chessTeam) {
        this.sender = sender;
        this.accept = accept;
        this.chessTeam = chessTeam;
    }

    public ChessAcceptInviteC2CPacket(PacketByteBuf raw) {
        this.sender = raw.readString();
        this.accept = raw.readBoolean();
        this.chessTeam = raw.readEnumConstant(ChessTeam.class);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(this.sender);
        buf.writeBoolean(this.accept);
        buf.writeEnumConstant(this.chessTeam);
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
