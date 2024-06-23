package net.earthcomputer.clientcommands.mixin.commands.generic;

import net.earthcomputer.clientcommands.command.ReplyCommand;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "handlePlayerChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    private void onHandlePlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet.chatType().chatType().is(ChatType.MSG_COMMAND_INCOMING) || packet.chatType().chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
            level.players()
                .stream()
                .filter(player -> player.getUUID().equals(packet.sender()))
                .findAny()
                .map(player -> player.getGameProfile().getName())
                .ifPresent(ReplyCommand::setMostRecentWhisper);
        }
    }
}
