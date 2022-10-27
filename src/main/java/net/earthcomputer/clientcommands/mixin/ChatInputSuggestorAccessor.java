package net.earthcomputer.clientcommands.mixin;

import java.util.List;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {

    @Accessor("HIGHLIGHT_STYLES")
    static List<Style> getHighlightStyles() {
        throw new AssertionError();
    }

}
