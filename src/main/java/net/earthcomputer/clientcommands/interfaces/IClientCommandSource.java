package net.earthcomputer.clientcommands.interfaces;

import com.mojang.brigadier.suggestion.Suggestion;
import net.earthcomputer.clientcommands.command.Flag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IClientCommandSource {
    <T> T clientcommands_getFlag(Flag<T> arg);

    <T> IClientCommandSource clientcommands_withFlag(Flag<T> arg, T value);

    @Nullable
    List<Suggestion> clientcommands_filterSuggestions(List<Suggestion> suggestions);
}
