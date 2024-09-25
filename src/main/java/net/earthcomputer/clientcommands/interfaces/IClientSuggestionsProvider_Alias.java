package net.earthcomputer.clientcommands.interfaces;

public interface IClientSuggestionsProvider_Alias {
    void clientcommands_addSeenAlias(String alias);

    void clientcommands_removeSeenAlias(String alias);

    boolean clientcommands_isAliasSeen(String alias);
}
