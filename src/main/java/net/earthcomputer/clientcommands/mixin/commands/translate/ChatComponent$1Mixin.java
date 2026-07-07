package net.earthcomputer.clientcommands.mixin.commands.translate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.earthcomputer.clientcommands.interfaces.IChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/client/gui/components/ChatComponent$1")
public abstract class ChatComponent$1Mixin {
    @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean copyText(ChatComponent.ChatGraphicsAccess instance, int textTop, float opacity, FormattedCharSequence text, Operation<Boolean> original, GuiMessage.Line line) {
        if (shouldCallOriginal(instance, textTop, text, line)) {
            return original.call(instance, textTop, opacity, text);
        }
        // original.call() always returns false
        return false;
    }

    private static boolean shouldCallOriginal(ChatComponent.ChatGraphicsAccess instance, int textTop, FormattedCharSequence text, GuiMessage.Line line) {
        if (!(instance instanceof ChatComponent.ClickableTextOnlyGraphicsAccess clickableTextOnlyGraphicsAccess)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ChatScreen chatScreen)) {
            return true;
        }
        if (!((IChatScreen) chatScreen).clientcommands_isTranslating()) {
            return true;
        }
        if (!(clickableTextOnlyGraphicsAccess.output instanceof ActiveTextCollector.ClickableStyleFinder clickableStyleFinder)) {
            return true;
        }
        Font font = minecraft.font;
        ActiveTextCollector.Parameters parameters = clickableStyleFinder.defaultParameters();
        int leftX = TextAlignment.LEFT.calculateLeft(0, font, text);
        GuiTextRenderState renderState = new GuiTextRenderState(font, text, parameters.pose(), leftX, textTop, ARGB.white(parameters.opacity()), 0, true, true, parameters.scissor());
        boolean[] found = {false};
        ActiveTextCollector.findElementUnderCursor(renderState, clickableStyleFinder.testX, clickableStyleFinder.testY, _ -> {
            minecraft.keyboardHandler.setClipboard(line.parent().content().getString());
            found[0] = true;
        });
        return !found[0];
    }
}
