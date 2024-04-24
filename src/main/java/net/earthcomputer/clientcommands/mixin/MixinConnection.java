package net.earthcomputer.clientcommands.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    @Shadow @Final private PacketFlow receiving;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private void onPacketReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            ListenCommand.onPacket(packet, ListenCommand.PacketFlow.CLIENTBOUND);
        }
    }

    @Inject(method = "doSendPacket", at = @At("HEAD"))
    private void onPacketSend(Packet<?> packet, @Nullable PacketSendListener sendListener, boolean flush, CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            ListenCommand.onPacket(packet, ListenCommand.PacketFlow.SERVERBOUND);
        }
    }
}
