package net.earthcomputer.clientcommands.c2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class C2CFriendlyByteBuf extends RegistryFriendlyByteBuf {
    @Nullable
    private final String sender;
    @Nullable
    private final UUID senderUUID;

    public C2CFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess, @Nullable String sender, @Nullable UUID senderUUID) {
        super(source, registryAccess);
        this.sender = sender;
        this.senderUUID = senderUUID;
    }

    @Nullable
    public String getSender() {
        return this.sender;
    }

    @Nullable
    public UUID getSenderUUID() {
        return this.senderUUID;
    }
}
