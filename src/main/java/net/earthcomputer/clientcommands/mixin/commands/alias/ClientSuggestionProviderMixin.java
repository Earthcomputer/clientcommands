package net.earthcomputer.clientcommands.mixin.commands.alias;

import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider_Alias;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(ClientSuggestionProvider.class)
public class ClientSuggestionProviderMixin implements IClientSuggestionsProvider_Alias {
    @Unique
    private final Set<String> seenAliases = new HashSet<>();

    @Override
    public void clientcommands_addSeenAlias(String alias) {
        seenAliases.add(alias);
    }

    @Override
    public void clientcommands_removeSeenAlias(String alias) {
        seenAliases.remove(alias);
    }

    @Override
    public boolean clientcommands_isAliasSeen(String alias) {
        return seenAliases.contains(alias);
    }
}
