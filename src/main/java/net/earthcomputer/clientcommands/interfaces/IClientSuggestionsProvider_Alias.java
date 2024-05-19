package net.earthcomputer.clientcommands.interfaces;

public interface IClientSuggestionsProvider_Alias {
    void clientcommands_addSeenAlias(String alias);

    boolean clientcommands_isAliasSeen(String alias);
}
