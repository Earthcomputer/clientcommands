package net.earthcomputer.clientcommands.interfaces;

import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.minecraft.network.ProtocolInfo;

public interface IClientPacketListener_C2C {
    ProtocolInfo<C2CPacketListener> clientcommands_getC2CProtocolInfo();
}
