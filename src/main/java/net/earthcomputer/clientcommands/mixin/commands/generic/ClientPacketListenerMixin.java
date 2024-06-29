package net.earthcomputer.clientcommands.mixin.commands.generic;

import net.earthcomputer.clientcommands.command.ReplyCommand;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow public abstract @Nullable PlayerInfo getPlayerInfo(UUID uniqueId);

    @Inject(method = "handlePlayerChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    private void onHandlePlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet.chatType().chatType().is(ChatType.MSG_COMMAND_INCOMING) || packet.chatType().chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
            PlayerInfo info = getPlayerInfo(packet.sender());
            if (info != null) {
                ReplyCommand.setMostRecentWhisper(info.getProfile().getName());
            }
        }
    }
}
