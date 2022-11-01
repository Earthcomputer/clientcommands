package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyArg(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ChatPreviewer;tryConsumeResponse(Ljava/lang/String;)Lnet/minecraft/client/network/ChatPreviewer$Response;"))
    private String onSendMessage(String message) {
        return VarCommand.replaceVariables(message);
    }
}
