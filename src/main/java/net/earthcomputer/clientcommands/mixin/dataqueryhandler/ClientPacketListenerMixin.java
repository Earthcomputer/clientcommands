package net.earthcomputer.clientcommands.mixin.dataqueryhandler;

import net.earthcomputer.clientcommands.features.ClientcommandsDataQueryHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements ClientcommandsDataQueryHandler.IClientPlayNetworkHandler {
    @Unique
    private final ClientcommandsDataQueryHandler ccDataQueryHandler = new ClientcommandsDataQueryHandler((ClientPacketListener) (Object) this);

    @Inject(method = "handleTagQueryPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onHandleTagQueryPacket(ClientboundTagQueryPacket packet, CallbackInfo ci) {
        if (ccDataQueryHandler.handleQueryResponse(packet.getTransactionId(), packet.getTag())) {
            ci.cancel();
        }
    }

    @Override
    public ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler() {
        return ccDataQueryHandler;
    }
}
