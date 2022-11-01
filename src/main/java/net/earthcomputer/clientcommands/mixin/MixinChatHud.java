package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.command.WhisperEncryptedCommand;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V", at = @At("HEAD"), argsOnly = true)
    private Text modifyMessage(Text text) {
        if (text.getString().contains("CCENC:")) {
            return WhisperEncryptedCommand.decryptTest(text);
        }
        return text;
    }
}
