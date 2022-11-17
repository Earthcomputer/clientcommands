package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.c2c.*;
import net.earthcomputer.clientcommands.interfaces.IProfileKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Optional;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V", at = @At("HEAD"), cancellable = true)
    private void onC2CPacket(Text message, MessageSignatureData signature, int ticks, MessageIndicator indicator, boolean refresh, CallbackInfo ci) {
        handleIfPacket(message, ci);
    }

    private void handleIfPacket(Text content, CallbackInfo ci) {
        String string = content.getString();
        int index = string.indexOf("CCENC:");
        if (index == -1) {
            return;
        }
        String packetString = string.substring(index + 6);
        if (!TempRules.acceptC2CPackets) {
            if (OutgoingPacketFilter.removeIfContains(packetString)) {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.sentC2CPacket"));
            } else {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.receivedC2CPacket").styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, content))));
            }
            ci.cancel();
            return;
        }
        if (OutgoingPacketFilter.removeIfContains(packetString)) {
            ci.cancel();
            return;
        }
        if (handleC2CPacket(packetString)) {
            ci.cancel();
        } else {
            this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.malformedPacket").formatted(Formatting.RED));
        }
    }

    private static boolean handleC2CPacket(String content) {
        byte[] encrypted = ConversionHelper.BaseUTF8.fromUnicode(content);
        encrypted = Arrays.copyOf(encrypted, 256);
        Optional<PrivateKey> key = ((IProfileKeys) MinecraftClient.getInstance().getProfileKeys()).getPrivateKey();
        if (key.isEmpty()) {
            return false;
        }
        byte[] decrypted = ConversionHelper.RsaEcb.decrypt(encrypted, key.get());
        if (decrypted == null) {
            return false;
        }
        byte[] uncompressed = ConversionHelper.Gzip.uncompress(decrypted);
        StringBuf buf = new StringBuf(new String(uncompressed, StandardCharsets.UTF_8));
        int id = buf.readInt();
        CCPacket ccPacket = CCPacketHandler.createPacket(id, buf);
        if (ccPacket == null) {
            return false;
        }
        if (buf.getRemainingLength() > 0) {
            return false;
        }
        ccPacket.apply(CCNetworkHandler.getInstance());
        return true;
    }
}
