package net.earthcomputer.clientcommands.c2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class RawPacketInfo extends RegistryFriendlyByteBuf {
    private final String sender;

    public RawPacketInfo(ByteBuf source, RegistryAccess registryAccess, String sender) {
        super(source, registryAccess);
        this.sender = sender;
    }

    public String getSender() {
        return this.sender;
    }
}
