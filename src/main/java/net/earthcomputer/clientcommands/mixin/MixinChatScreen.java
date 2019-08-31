package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.server.command.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @Shadow private ParseResults<CommandSource> parseResults;

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "updateCommand", at = @At("RETURN"))
    public void onUpdateCommand(CallbackInfo ci) {
        boolean isClientCommand;
        if (parseResults == null) {
            isClientCommand = false;
        } else {
            StringReader reader = new StringReader(parseResults.getReader().getString());
            reader.skip(); // /
            String command = reader.canRead() ? reader.readUnquotedString() : "";
            isClientCommand = ClientCommandManager.isClientSideCommand(command);
        }

        chatField.setMaxLength(isClientCommand ? 32500 : 256);
    }

}
