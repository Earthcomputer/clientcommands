package net.earthcomputer.clientcommands.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {

    @Accessor("ARGUMENT_STYLES")
    static List<Style> getArgumentStyles() {
        throw new AssertionError();
    }

}
