package net.earthcomputer.clientcommands.mixin.c2c;

import com.mojang.authlib.GameProfile;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.OutgoingPacketFilter;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
    @Inject(method = "showMessageToPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatTrustLevel;createTag(Lnet/minecraft/network/chat/PlayerChatMessage;)Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"), cancellable = true)
    private void onC2CPacket(ChatType.Bound boundChatType, PlayerChatMessage message, Component decoratedMessage, GameProfile sender, boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> cir) {
        String string = message.signedContent();
        int index = string.indexOf(C2CPacketHandler.C2C_PACKET_HEADER);
        if (index == -1) {
            return;
        }
        String packetString = string.substring(index + C2CPacketHandler.C2C_PACKET_HEADER.length());
        if (!Configs.acceptC2CPackets) {
            if (OutgoingPacketFilter.removeIfContains(packetString)) {
                ClientCommandHelper.sendFeedback("c2cpacket.sentC2CPacket");
            } else {
                ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.receivedC2CPacket").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(boundChatType.decorate(message.decoratedContent())))));
            }
            cir.setReturnValue(false);
            return;
        }
        if (OutgoingPacketFilter.removeIfContains(packetString)) {
            cir.setReturnValue(false);
            return;
        }
        if (C2CPacketHandler.handleC2CPacket(packetString, sender.name(), sender.id())) {
            cir.setReturnValue(true);
        } else {
            ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.malformedPacket").withStyle(ChatFormatting.RED));
        }
    }
}
