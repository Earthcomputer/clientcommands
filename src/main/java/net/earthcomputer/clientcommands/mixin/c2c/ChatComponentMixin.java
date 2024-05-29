package net.earthcomputer.clientcommands.mixin.c2c;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.OutgoingPacketFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void onC2CPacket(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        handleIfPacket(message, ci);
    }

    @Unique
    private void handleIfPacket(Component content, CallbackInfo ci) {
        String string = content.getString();
        int index = string.indexOf("CCENC:");
        if (index == -1) {
            return;
        }
        String packetString = string.substring(index + 6);
        if (!Configs.acceptC2CPackets) {
            if (OutgoingPacketFilter.removeIfContains(packetString)) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("c2cpacket.sentC2CPacket"));
            } else {
                this.minecraft.gui.getChat().addMessage(Component.translatable("c2cpacket.receivedC2CPacket").withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, content))));
            }
            ci.cancel();
            return;
        }
        if (OutgoingPacketFilter.removeIfContains(packetString)) {
            ci.cancel();
            return;
        }
        if (C2CPacketHandler.handleC2CPacket(packetString)) {
            ci.cancel();
        } else {
            this.minecraft.gui.getChat().addMessage(Component.translatable("c2cpacket.malformedPacket").withStyle(ChatFormatting.RED));
        }
    }
}
