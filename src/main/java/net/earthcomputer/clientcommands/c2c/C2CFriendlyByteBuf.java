package net.earthcomputer.clientcommands.c2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class C2CFriendlyByteBuf extends RegistryFriendlyByteBuf {
    private final String sender;

    public C2CFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess, String sender) {
        super(source, registryAccess);
        this.sender = sender;
    }

    public String getSender() {
        return this.sender;
    }
}
