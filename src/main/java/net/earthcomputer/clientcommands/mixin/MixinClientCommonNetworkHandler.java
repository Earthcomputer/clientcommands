package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.ServerBrandManager;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class MixinClientCommonNetworkHandler {
    @Shadow @Nullable protected String brand;

    @Inject(method = "onCustomPayload(Lnet/minecraft/network/packet/s2c/common/CustomPayloadS2CPacket;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientCommonNetworkHandler;brand:Ljava/lang/String;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void onBrand(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        ServerBrandManager.setServerBrand(brand);
    }
}
