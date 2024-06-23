package net.earthcomputer.clientcommands.mixin.commands.generic;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.AutoPrefixCommand;
import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyReceiver(method = "handleChatInput", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", remap = false))
    private String onHandleChatInput(String instance, String slash, @Local(argsOnly = true) LocalRef<String> message) {
        String prefix = AutoPrefixCommand.getCurrentPrefix();
        if (prefix == null || instance.startsWith("/")) {
            prefix = "";
        } else {
            prefix = prefix + " ";
        }

        String command = VarCommand.replaceVariables(prefix + instance);
        if (command.startsWith("/")) {
            ClientCommands.sendCommandExecutionToServer(command.substring(1));
        }

        message.set(command);
        return command;
    }
}
