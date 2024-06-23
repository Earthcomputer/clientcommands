package net.earthcomputer.clientcommands.mixin.commands.generic;

import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.AutoPrefixCommand;
import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyVariable(method = "handleChatInput", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", remap = false), argsOnly = true)
    private String onHandleChatInput(String message) {
        String prefix = AutoPrefixCommand.getCurrentPrefix();
        if (prefix == null || message.startsWith("/")) {
            prefix = "";
        } else {
            prefix = prefix + " ";
            System.out.println(prefix.charAt(0));
        }

        String command = VarCommand.replaceVariables(prefix + message);
        if (command.startsWith("/")) {
            ClientCommands.sendCommandExecutionToServer(command.substring(1));
        }
        return command;
    }
}
