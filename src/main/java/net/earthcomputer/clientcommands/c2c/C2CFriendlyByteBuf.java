package net.earthcomputer.clientcommands.c2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public class C2CFriendlyByteBuf extends RegistryFriendlyByteBuf {
    private final String sender;
    private final UUID senderUUID;

    public C2CFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess, String sender, UUID senderUUID) {
        super(source, registryAccess);
        this.sender = sender;
        this.senderUUID = senderUUID;
    }

    public String getSender() {
        return this.sender;
    }

    public UUID getSenderUUID() {
        return this.senderUUID;
    }
}
