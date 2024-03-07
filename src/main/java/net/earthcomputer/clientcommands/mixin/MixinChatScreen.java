package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Shadow protected EditBox input;

    @Unique
    @Nullable
    private Integer oldMaxLength = null;

    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyVariable(method = "handleChatInput", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", remap = false), argsOnly = true)
    private String onHandleChatInput(String message) {
        String command = VarCommand.replaceVariables(message);
        if (command.startsWith("/")) {
            ClientCommands.sendCommandExecutionToServer(command.substring(1));
        }
        return command;
    }

    @Inject(method = "onEdited", at = @At("HEAD"))
    private void onEdited(String value, CallbackInfo ci) {
        if (value.startsWith("/") && ClientCommands.isClientcommandsCommand(value.substring(1).split(" ")[0])) {
            if (oldMaxLength == null) {
                oldMaxLength = input.maxLength;
            }
            input.setMaxLength(32767);
        } else {
            // TODO: what if other mods try to do the same thing?
            if (oldMaxLength != null) {
                input.setMaxLength(oldMaxLength);
                oldMaxLength = null;
            }
        }
    }
}
