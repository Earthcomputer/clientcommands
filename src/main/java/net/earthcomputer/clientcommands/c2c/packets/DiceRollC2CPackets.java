package net.earthcomputer.clientcommands.c2c.packets;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketHandler;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.minecraft.network.PacketByteBuf;

import java.math.BigInteger;
import java.util.BitSet;

public class DiceRollC2CPackets {
    // use a diffie hellman key exchange in order to ensure that the coinflip is fair

    public static class DiceRollInitC2CPacket implements C2CPacket {
        public final String sender;
        public final int sides;
        public final byte[] ABHash;

        public DiceRollInitC2CPacket(String sender, int sides, byte[] ABHash) {
            this.sender = sender;
            this.sides = sides;
            this.ABHash = ABHash;
        }

        public DiceRollInitC2CPacket(PacketByteBuf raw) {
            this.sender = raw.readString();
            this.sides = raw.readInt();
            this.ABHash = raw.readByteArray();
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeString(this.sender);
            buf.writeInt(this.sides);
            buf.writeByteArray(this.ABHash);
        }

        @Override
        public void apply(CCPacketListener listener) throws CommandSyntaxException {
            listener.onCoinflipInitC2CPacket(this);
        }
    }

    public static class DiceRollAcceptedC2CPacket implements C2CPacket {
        public final String sender;
        public final BigInteger AB;

        public DiceRollAcceptedC2CPacket(String sender, BigInteger publicKey) {
            this.sender = sender;
            this.AB = publicKey;
        }

        public DiceRollAcceptedC2CPacket(PacketByteBuf stringBuf) {
            this.sender = stringBuf.readString();
            this.AB = new BigInteger(stringBuf.readBitSet().toByteArray());
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeString(this.sender);
            buf.writeBitSet(BitSet.valueOf(this.AB.toByteArray()));
        }

        @Override
        public void apply(CCPacketListener listener) throws CommandSyntaxException {
            listener.onCoinflipAcceptedC2CPacket(this);
        }
    }

    public static class DiceRollResultC2CPacket implements C2CPacket {
        public final String sender;
        public final BigInteger s;

        public DiceRollResultC2CPacket(String sender, BigInteger s) {
            this.sender = sender;
            this.s = s;
        }

        public DiceRollResultC2CPacket(PacketByteBuf stringBuf) {
            this.sender = stringBuf.readString();
            this.s = new BigInteger(stringBuf.readBitSet().toByteArray());
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeString(this.sender);
            buf.writeBitSet(BitSet.valueOf(this.s.toByteArray()));
        }

        @Override
        public void apply(CCPacketListener listener) {
            listener.onCoinflipResultC2CPacket(this);
        }
    }

    public static void register() {
        CCPacketHandler.register(DiceRollInitC2CPacket.class, DiceRollInitC2CPacket::new);
        CCPacketHandler.register(DiceRollAcceptedC2CPacket.class, DiceRollAcceptedC2CPacket::new);
        CCPacketHandler.register(DiceRollResultC2CPacket.class, DiceRollResultC2CPacket::new);
    }
}
