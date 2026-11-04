package net.earthcomputer.clientcommands.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent by the server when a player joins and whenever the permissions change, containing the flags of the allowed
 * {@link ServerPermission}s.
 */
public record PermissionsPayload(int flags) implements CustomPacketPayload {
    public static final Type<PermissionsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("clientcommands", "permissions"));
    public static final StreamCodec<ByteBuf, PermissionsPayload> CODEC = ByteBufCodecs.VAR_INT.map(PermissionsPayload::new, PermissionsPayload::flags);

    @Override
    public Type<PermissionsPayload> type() {
        return TYPE;
    }
}
