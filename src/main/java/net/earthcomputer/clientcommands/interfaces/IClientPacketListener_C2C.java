package net.earthcomputer.clientcommands.interfaces;

import io.netty.buffer.ByteBuf;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.function.Function;

public interface IClientPacketListener_C2C {
    Function<ByteBuf, RegistryFriendlyByteBuf> clientcommands_getBufWrapper();
    ProtocolInfo<C2CPacketListener> clientcommands_getC2CProtocolInfo();
}
