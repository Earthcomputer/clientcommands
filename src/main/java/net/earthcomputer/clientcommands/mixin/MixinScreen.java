package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.command.VarCommand;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Screen.class)
public class MixinScreen {

    // replace the text before the Fabric Command API executes it,
    // but ensure the message is added to the history in its raw form.
    @ModifyArg(method = "sendMessage(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendChatMessage(Ljava/lang/String;)V"))
    private String onSendMessage(String message) {
        return VarCommand.replaceVariables(message);
    }
}
