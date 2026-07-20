package net.earthcomputer.clientcommands.mixin.commands.translate;

import net.earthcomputer.clientcommands.command.TranslateCommand;
import net.earthcomputer.clientcommands.interfaces.IChatScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements IChatScreen {

    @Unique
    private boolean isTranslating = false;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "onEdited", at = @At("TAIL"))
    private void onEdited(String value, CallbackInfo ci) {
        this.isTranslating = value.startsWith(Commands.COMMAND_PREFIX + TranslateCommand.COMMAND_NAME);
    }

    @Override
    public boolean clientcommands_isTranslating() {
        return this.isTranslating;
    }
}
