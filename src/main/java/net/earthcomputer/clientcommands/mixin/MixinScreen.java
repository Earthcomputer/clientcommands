package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.RunnableClickEventActionHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true)
    private void executeCode(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null) {
            return;
        }
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return;
        }
        if (clickEvent.getAction() != ClickEvent.Action.CHANGE_PAGE) {
            return;
        }
        String value = clickEvent.getValue();
        Runnable runnable = RunnableClickEventActionHelper.runnables.get(value);
        if (runnable == null) {
            return;
        }
        runnable.run();
        cir.setReturnValue(true);
    }
}
