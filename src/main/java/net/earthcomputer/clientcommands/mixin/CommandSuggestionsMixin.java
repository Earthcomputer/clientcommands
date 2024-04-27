package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.earthcomputer.clientcommands.command.Flag;
import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
    @Shadow private @Nullable ParseResults<SharedSuggestionProvider> currentParse;
    @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Inject(
        method = "updateCommandInfo",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;getCommands()Lcom/mojang/brigadier/CommandDispatcher;")),
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;pendingSuggestions:Ljava/util/concurrent/CompletableFuture;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER, ordinal = 0)
    )
    private void filterSuggestions(CallbackInfo ci) {
        assert this.pendingSuggestions != null && this.currentParse != null;
        this.pendingSuggestions = this.pendingSuggestions.thenApply(suggestions -> {
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
