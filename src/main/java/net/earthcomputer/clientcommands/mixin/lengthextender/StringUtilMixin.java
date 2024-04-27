package net.earthcomputer.clientcommands.mixin.lengthextender;

import net.earthcomputer.clientcommands.features.ChatLengthExtender;
import net.minecraft.SharedConstants;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(StringUtil.class)
public class StringUtilMixin {
    @ModifyConstant(method = "trimChatMessage", constant = @Constant(intValue = SharedConstants.MAX_CHAT_LENGTH))
    private static int modifyMaxChatLength(int oldMax) {
        if (ChatLengthExtender.currentLengthExtension != null) {
            return ChatLengthExtender.currentLengthExtension;
        }
        return oldMax;
    }
}
