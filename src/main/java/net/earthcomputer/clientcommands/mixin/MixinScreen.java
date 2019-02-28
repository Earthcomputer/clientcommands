package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.minecraft.client.gui.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "method_2213", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendChatMessage(Ljava/lang/String;)V"), cancellable = true)
    public void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.startsWith("/")) {
            StringReader reader = new StringReader(message);
            reader.skip();
            int cursor = reader.getCursor();
            String commandName = reader.canRead() ? reader.readUnquotedString() : "";
            reader.setCursor(cursor);
            if (ClientCommandManager.isClientSideCommand(commandName)) {
                ClientCommandManager.executeCommand(reader, message);
                ci.cancel();
            }
        }
    }

}
