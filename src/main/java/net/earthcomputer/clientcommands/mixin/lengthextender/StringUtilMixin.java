package net.earthcomputer.clientcommands.mixin.lengthextender;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.earthcomputer.clientcommands.features.ChatLengthExtender;
import net.minecraft.SharedConstants;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StringUtil.class, priority = 900) // lower priority for ViaFabricPlus compatibility
public class StringUtilMixin {
    @ModifyExpressionValue(method = "trimChatMessage", at = @At(value = "CONSTANT", args = "intValue=" + SharedConstants.MAX_CHAT_LENGTH))
    private static int modifyMaxChatLength(int oldMax) {
        if (ChatLengthExtender.currentLengthExtension != null) {
            return ChatLengthExtender.currentLengthExtension;
        }
        return oldMax;
    }
}
