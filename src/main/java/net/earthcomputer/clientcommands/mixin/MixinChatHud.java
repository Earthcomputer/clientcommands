package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.command.WhisperEncryptedCommand;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text modifyMessage(Text text) {
        return WhisperEncryptedCommand.decryptTest(text);
    }
}
