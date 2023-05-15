package net.earthcomputer.clientcommands.features;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class BypassRequiredResourcePackScreen extends ConfirmScreen {

    private final Runnable bypassCallback;

    public BypassRequiredResourcePackScreen(BooleanConsumer callback, Runnable bypassCallback, Text message) {
        super(callback, Text.translatable("multiplayer.requiredTexturePrompt.line1"), message, ScreenTexts.PROCEED, Text.translatable("menu.disconnect"));
        this.bypassCallback = bypassCallback;
    }

    @Override
    protected void addButtons(int y) {
        super.addButtons(y);
        this.addButton(ButtonWidget.builder(Text.translatable("resourcePackBypass.bypass"), button -> this.bypassCallback.run()).dimensions(this.width / 2 - 155 / 2, y + 30, 150, 20).build());
    }
}
