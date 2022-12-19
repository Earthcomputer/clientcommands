package net.earthcomputer.clientcommands.c2c.packets;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCPacketHandler;
import net.earthcomputer.clientcommands.c2c.CCPacketListener;
import net.minecraft.network.PacketByteBuf;

import java.math.BigInteger;
import java.util.BitSet;

public class CoinflipC2CPackets {
    // use a diffie hellman key exchange in order to ensure that the coinflip is fair

    public static class CoinflipInitC2CPacket implements C2CPacket {
        public final String sender;
        public final BigInteger AB;

        public CoinflipInitC2CPacket(String sender, BigInteger publicKey) {
            this.sender = sender;
            this.AB = publicKey;
        }

        public CoinflipInitC2CPacket(PacketByteBuf stringBuf) {
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
            listener.onCoinflipInitC2CPacket(this);
        }
    }

    public static class CoinflipResultC2CPacket implements C2CPacket {
        public final String sender;
        public final BigInteger s;

        public CoinflipResultC2CPacket(String sender, BigInteger s) {
            this.sender = sender;
            this.s = s;
        }

        public CoinflipResultC2CPacket(PacketByteBuf stringBuf) {
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
        CCPacketHandler.register(CoinflipInitC2CPacket.class, CoinflipInitC2CPacket::new);
        CCPacketHandler.register(CoinflipResultC2CPacket.class, CoinflipResultC2CPacket::new);
    }
}
