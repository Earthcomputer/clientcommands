package net.earthcomputer.clientcommands.oldservergametest.mixin;

import net.earthcomputer.clientcommands.oldservergametest.FishingTests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void onHandleSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        // the packet is handled on the network thread first, which then schedules it on the client thread
        if (Minecraft.getInstance().isSameThread()) {
            FishingTests.onSetEntityData(packet);
        }
    }
}
