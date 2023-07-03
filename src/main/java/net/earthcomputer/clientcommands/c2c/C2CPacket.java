package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.PacketByteBuf;

public interface C2CPacket {
    void write(PacketByteBuf buf);

    void apply(CCPacketListener listener) throws CommandSyntaxException;
}
