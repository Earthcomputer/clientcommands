package net.earthcomputer.clientcommands.c2c.packets;

import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.earthcomputer.clientcommands.c2c.chess.ChessTeam;
import net.minecraft.network.PacketByteBuf;

public class ChessInviteC2CPacket implements C2CPacket {

    private final String sender;
    private final ChessTeam chessTeam;

    public ChessInviteC2CPacket(String sender, ChessTeam set) {
        this.sender = sender;
        this.chessTeam = set;
    }

    public ChessInviteC2CPacket(PacketByteBuf raw) {
        this.sender = raw.readString();
        this.chessTeam = raw.readEnumConstant(ChessTeam.class);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(this.sender);
        buf.writeEnumConstant(this.chessTeam);
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
