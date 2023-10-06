package net.earthcomputer.clientcommands.interfaces;

import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestion;
import net.earthcomputer.clientcommands.command.Argument;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IClientCommandSource {
    <T> T clientcommands_getArg(Argument<T> arg);

    <T> IClientCommandSource clientcommands_withArg(Argument<T> arg, T value);

    @Nullable
    List<Suggestion> clientcommands_filterSuggestions(List<Suggestion> suggestions);
}
