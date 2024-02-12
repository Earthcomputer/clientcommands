package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.ServerBrandManager;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {
    @Shadow @Nullable protected String serverBrand;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;serverBrand:Ljava/lang/String;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void onBrand(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ServerBrandManager.setServerBrand(serverBrand);
    }
}
