package net.earthcomputer.clientcommands.mixin;

import java.util.List;
import net.minecraft.client.gui.screen.CommandSuggestor;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestor.class)
public interface CommandSuggestorAccessor {

    @Accessor("HIGHLIGHT_FORMATTINGS")
    static List<Style> getHighlightFormattings() {
        throw new AssertionError();
    }

}
