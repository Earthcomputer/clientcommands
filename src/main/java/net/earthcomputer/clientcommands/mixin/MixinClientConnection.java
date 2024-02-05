package net.earthcomputer.clientcommands.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Shadow @Final private NetworkSide side;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V"))
    private void onPacketReceive(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.side == NetworkSide.CLIENTBOUND) {
            ListenCommand.onPacket(packet, NetworkSide.CLIENTBOUND);
        }
    }

    @Inject(method = "sendInternal", at = @At("HEAD"))
    private void onPacketSend(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        if (this.side == NetworkSide.CLIENTBOUND) {
            ListenCommand.onPacket(packet, NetworkSide.SERVERBOUND);
        }
    }
}
