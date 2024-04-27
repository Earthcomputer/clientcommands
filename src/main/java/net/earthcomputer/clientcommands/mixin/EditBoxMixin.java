package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.ChatLengthExtender;
import net.earthcomputer.clientcommands.interfaces.IEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(EditBox.class)
public abstract class EditBoxMixin implements IEditBox {
    @Shadow
    private int cursorPos;
    @Shadow
    private int highlightPos;

    @Shadow public abstract void setMaxLength(int length);

    @Shadow public int maxLength;

    @Shadow public abstract String getValue();

    @Shadow private Predicate<String> filter;
    @Unique
    @Nullable
    private Integer oldMaxLength = null;
    @Unique
    private boolean clientCommandLengthExtension;

    @Override
    public void clientcommands_setClientCommandLengthExtension() {
        clientCommandLengthExtension = true;
    }

    @Inject(method = "insertText", at = @At("HEAD"))
    private void onInsertText(String textToWrite, CallbackInfo ci) {
        int startSelection = Math.min(cursorPos, highlightPos);
        int endSelection = Math.max(cursorPos, highlightPos);
        String newText = new StringBuilder(getValue()).replace(startSelection, endSelection, StringUtil.filterText(textToWrite)).toString();
        if (!this.filter.test(newText)) {
            return;
        }

        updateTextMaxLength(newText);
    }

    @Inject(method = "setValue", at = @At("HEAD"))
    private void onSetValue(String newText, CallbackInfo ci) {
        updateTextMaxLength(newText);
    }

    @Inject(method = "onValueChange", at = @At("HEAD"))
    private void onValueChange(String newText, CallbackInfo ci) {
        updateTextMaxLength(newText);
    }

    @Unique
    private void updateTextMaxLength(String newValue) {
        if (!clientCommandLengthExtension) {
            return;
        }

        if (ChatLengthExtender.isClientcommandsCommand(newValue)) {
            if (oldMaxLength == null) {
                oldMaxLength = maxLength;
            }
            setMaxLength(ChatLengthExtender.EXTENDED_LENGTH);
        } else {
            // TODO: what if other mods try to do the same thing?
            if (oldMaxLength != null) {
                setMaxLength(oldMaxLength);
                oldMaxLength = null;
            }
        }
    }
}
