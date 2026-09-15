package net.earthcomputer.clientcommands.mixin.roundtripfence;

import net.earthcomputer.clientcommands.interfaces.IClientPacketListener;
import net.earthcomputer.clientcommands.util.RoundTripFence;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements IClientPacketListener {
    @Unique
    private final RoundTripFence roundTripFence = new RoundTripFence((ClientPacketListener) (Object) this);

    @Override
    public RoundTripFence clientcommands_getRoundTripFence() {
        return roundTripFence;
    }

    @Inject(method = "handleAwardStats", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onAwardStats(CallbackInfo ci) {
        roundTripFence.onReceiveStats();
    }
}
