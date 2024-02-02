package net.earthcomputer.clientcommands.mixin;

import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.c2c.C2CPacket;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.CCPacketHandler;
import net.earthcomputer.clientcommands.c2c.ConversionHelper;
import net.earthcomputer.clientcommands.c2c.OutgoingPacketFilter;
import net.earthcomputer.clientcommands.interfaces.IHasPrivateKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.FriendlyByteBuf;
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

import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Optional;

@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", at = @At("HEAD"), cancellable = true)
    private void onC2CPacket(Component message, MessageSignature signature, int ticks, GuiMessageTag indicator, boolean refresh, CallbackInfo ci) {
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
                this.minecraft.gui.getChat().addMessage(Component.translatable("ccpacket.sentC2CPacket"));
            } else {
                this.minecraft.gui.getChat().addMessage(Component.translatable("ccpacket.receivedC2CPacket").withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, content))));
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
            this.minecraft.gui.getChat().addMessage(Component.translatable("ccpacket.malformedPacket").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private static boolean handleC2CPacket(String content) {
        byte[] encrypted = ConversionHelper.BaseUTF8.fromUnicode(content);
        // round down to multiple of 256 bytes
        int length = encrypted.length & ~0xFF;
        // copy to new array of arrays
        byte[][] encryptedArrays = new byte[length / 256][];
        for (int i = 0; i < length; i += 256) {
            encryptedArrays[i / 256] = Arrays.copyOfRange(encrypted, i, i + 256);
        }
        if (!(Minecraft.getInstance().getProfileKeyPairManager() instanceof IHasPrivateKey privateKeyHolder)) {
            return false;
        }
        Optional<PrivateKey> key = privateKeyHolder.getPrivateKey();
        if (key.isEmpty()) {
            return false;
        }
        // decrypt
        int len = 0;
        byte[][] decryptedArrays = new byte[encryptedArrays.length][];
        for (int i = 0; i < encryptedArrays.length; i++) {
            decryptedArrays[i] = ConversionHelper.RsaEcb.decrypt(encryptedArrays[i], key.get());
            if (decryptedArrays[i] == null) {
                return false;
            }
            len += decryptedArrays[i].length;
        }
        // copy to new array
        byte[] decrypted = new byte[len];
        int pos = 0;
        for (byte[] decryptedArray : decryptedArrays) {
            System.arraycopy(decryptedArray, 0, decrypted, pos, decryptedArray.length);
            pos += decryptedArray.length;
        }
        byte[] uncompressed = ConversionHelper.Gzip.uncompress(decrypted);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed));
        int id = buf.readInt();
        C2CPacket c2CPacket = CCPacketHandler.createPacket(id, buf);
        if (c2CPacket == null) {
            return false;
        }
        if (buf.readableBytes() > 0) {
            return false;
        }
        try {
            c2CPacket.apply(CCNetworkHandler.getInstance());
        } catch (Exception e) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.nullToEmpty(e.getMessage()));
            e.printStackTrace();
        }
        return true;
    }
}
