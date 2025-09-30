package net.earthcomputer.clientcommands.mixin.commands.generic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.earthcomputer.clientcommands.command.Flag;
import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
    @Shadow
    @Nullable
    private ParseResults<SharedSuggestionProvider> currentParse;

    @ModifyExpressionValue(
        method = "updateCommandInfo",
        at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;", remap = false)
    )
    private CompletableFuture<Suggestions> filterSuggestions(CompletableFuture<Suggestions> original) {
        assert currentParse != null;
        return original.thenApply(suggestions -> {
            SharedSuggestionProvider source = Flag.getActualSource(this.currentParse);
            if (source instanceof IClientSuggestionsProvider mySource) {
                List<Suggestion> newSuggestions = mySource.clientcommands_filterSuggestions(suggestions.getList());
                return newSuggestions == null ? suggestions : new Suggestions(suggestions.getRange(), newSuggestions);
            } else {
                return suggestions;
            }
        });
    }
}
