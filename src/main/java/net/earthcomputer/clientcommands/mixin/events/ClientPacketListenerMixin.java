package net.earthcomputer.clientcommands.mixin.events;

import net.earthcomputer.clientcommands.event.MoreClientEntityEvents;
import net.earthcomputer.clientcommands.event.MoreClientEvents;
import net.earthcomputer.clientcommands.util.EstimatedServerTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    public void onHandleAddEntityPre(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        MoreClientEntityEvents.PRE_ADD_MAYBE_ON_NETWORK_THREAD.invoker().onAddEntity(packet);
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    public void onHandleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        MoreClientEntityEvents.POST_ADD.invoker().onAddEntity(packet);
    }

    @Inject(method = "handleAddExperienceOrb", at = @At("TAIL"))
    public void onHandleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet, CallbackInfo ci) {
        MoreClientEntityEvents.POST_ADD_XP_ORB.invoker().onXpOrb(packet);
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void onHandleSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            EstimatedServerTick.onSetTime(packet.getGameTime());
            MoreClientEvents.TIME_SYNC.invoker().onTimeSync(packet);
        } else {
            MoreClientEvents.TIME_SYNC_ON_NETWORK_THREAD.invoker().onTimeSync(packet);
        }
    }
}
