package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyVariable(method = "sendMessage", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", remap = false), argsOnly = true)
    private String onSendMessage(String message) {
        return VarCommand.replaceVariables(message);
    }
}
