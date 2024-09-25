package net.earthcomputer.clientcommands.mixin.commands.generic;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.suggestion.Suggestion;
import net.earthcomputer.clientcommands.command.Flag;
import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(ClientSuggestionProvider.class)
public class ClientSuggestionsProviderMixin implements IClientSuggestionsProvider {
    @Shadow
    @Final
    private ClientPacketListener connection;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private ImmutableMap<Flag<?>, Object> flags = ImmutableMap.of();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T clientcommands_getFlag(Flag<T> flag) {
        return (T) this.flags.getOrDefault(flag, flag.getDefaultValue());
    }

    @Override
    public <T> IClientSuggestionsProvider clientcommands_withFlag(Flag<T> flag, T value) {
        ClientSuggestionsProviderMixin source = (ClientSuggestionsProviderMixin) (Object) new ClientSuggestionProvider(this.connection, this.minecraft);
        source.flags = ImmutableMap.<Flag<?>, Object>builderWithExpectedSize(this.flags.size() + 1).putAll(this.flags).put(flag, value).build();
        return source;
    }

    @Override
    @Nullable
    public List<Suggestion> clientcommands_filterSuggestions(List<Suggestion> suggestions) {
        if (flags.isEmpty()) {
            return null;
        } else {
            return suggestions.stream().filter(suggestion -> {
                String text = suggestion.getText();
                return !Flag.isFlag(text) || flags.keySet().stream().noneMatch(arg -> !arg.isRepeatable() && (text.equals(arg.getFlag()) || text.equals(arg.getShortFlag())));
            }).toList();
        }
    }
}
