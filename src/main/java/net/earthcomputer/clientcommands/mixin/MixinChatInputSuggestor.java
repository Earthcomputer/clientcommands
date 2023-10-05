package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.earthcomputer.clientcommands.interfaces.IClientCommandSource;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.command.CommandSource;
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

@Mixin(ChatInputSuggestor.class)
public class MixinChatInputSuggestor {
    @Shadow private @Nullable ParseResults<CommandSource> parse;
    @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Inject(
        method = "refresh",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getCommandDispatcher()Lcom/mojang/brigadier/CommandDispatcher;")),
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;pendingSuggestions:Ljava/util/concurrent/CompletableFuture;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER, ordinal = 0)
    )
    private void filterSuggestions(CallbackInfo ci) {
        assert this.pendingSuggestions != null && this.parse != null;
        this.pendingSuggestions = this.pendingSuggestions.thenApply(suggestions -> {
            CommandSource source = this.parse.getContext().getSource();
            if (source instanceof IClientCommandSource mySource) {
                List<Suggestion> newSuggestions = mySource.clientcommands_filterSuggestions(suggestions.getList(), this.parse.getContext().getNodes());
                return newSuggestions == null ? suggestions : new Suggestions(suggestions.getRange(), newSuggestions);
            } else {
                return suggestions;
            }
        });
    }
}
