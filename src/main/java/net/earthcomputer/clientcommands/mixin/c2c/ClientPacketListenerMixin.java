package net.earthcomputer.clientcommands.mixin.c2c;

import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.C2CPacketListener;
import net.earthcomputer.clientcommands.interfaces.IClientPacketListener_C2C;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IClientPacketListener_C2C {
    @Shadow
    @Final
    private RegistryAccess.Frozen registryAccess;

    @Unique
    private final ProtocolInfo<C2CPacketListener> c2cProtocolInfo = C2CPacketHandler.PROTOCOL_UNBOUND.bind(RegistryFriendlyByteBuf.decorator(registryAccess));

    @Override
    public ProtocolInfo<C2CPacketListener> clientcommands_getC2CProtocolInfo() {
        return c2cProtocolInfo;
    }
}
