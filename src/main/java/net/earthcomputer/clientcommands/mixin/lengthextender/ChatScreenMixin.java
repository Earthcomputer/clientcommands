package net.earthcomputer.clientcommands.mixin.lengthextender;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.earthcomputer.clientcommands.features.ChatLengthExtender;
import net.earthcomputer.clientcommands.interfaces.IEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void init(CallbackInfo ci) {
        ((IEditBox) input).clientcommands_setClientCommandLengthExtension();
    }

    @WrapOperation(method = "normalizeChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;trimChatMessage(Ljava/lang/String;)Ljava/lang/String;"))
    private String normalizeChatMessage(String string, Operation<String> original) {
        Integer originalExtension = ChatLengthExtender.currentLengthExtension;
        ChatLengthExtender.currentLengthExtension = input.maxLength;
        try {
            return original.call(string);
        } finally {
            ChatLengthExtender.currentLengthExtension = originalExtension;
        }
    }
}
