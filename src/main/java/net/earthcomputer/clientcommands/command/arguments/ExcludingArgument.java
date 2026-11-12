package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.command.Flag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExcludingArgument<T, U extends ArgumentType<T>> implements ArgumentType<T> {
    private static final SimpleCommandExceptionType MATCHED_EXCLUSION_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.excluding"));

    public final U argument;
    public final ArrayList<Exclusion> exclusions;
    @Nullable
    private List<String> cachedExamples = null;

    public static <T, U extends ArgumentType<T>> ExcludingArgument<T, U> excluding(U argument, ArgumentType<?>... exclusions) {
        return new ExcludingArgument<>(argument, Arrays.stream(exclusions).map(Exclusion::forArgument).toList());
    }

    public static <T, U extends ArgumentType<T>> ExcludingArgument<T, U> excluding(U argument, Flag<?>... exclusions) {
        return new ExcludingArgument<>(argument, Arrays.stream(exclusions).map(Exclusion::forFlag).toList());
    }

    public static <T, U extends ArgumentType<T>> ExcludingArgument<T, U> excluding(U argument, Exclusion... exclusions) {
        return new ExcludingArgument<>(argument, List.of(exclusions));
    }

    private ExcludingArgument(U argument, List<Exclusion> exclusions) {
        this.argument = argument;
        this.exclusions = new ArrayList<>(exclusions);
    }

    @Override
    public Collection<String> getExamples() {
        if (cachedExamples == null) {
            cachedExamples = argument.getExamples()
                    .stream()
                    .filter(example -> !isExcluded(new StringReader(example)))
                    .toList();
        }

        return cachedExamples;
    }

    private boolean isExcluded(StringReader reader) {
        int cursor = reader.getCursor();
        for (Exclusion exclusion : exclusions) {
            boolean result = exclusion.canParse(reader);
            reader.setCursor(cursor);

            if (result) {
                return true;
            }
        }

        return false;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return argument.listSuggestions(context, builder);
    }

    @Override
    public T parse(StringReader reader) throws CommandSyntaxException {
        int cursorStart = reader.getCursor();
        T result = argument.parse(reader);
        int cursorEnd = reader.getCursor();

        reader.setCursor(cursorStart);
        if (isExcluded(reader)) {
            throw MATCHED_EXCLUSION_EXCEPTION.create();
        }

        reader.setCursor(cursorEnd);
        return result;
    }

    public interface Exclusion {
        boolean canParse(StringReader reader);

        static Exclusion forArgument(ArgumentType<?> argumentType) {
            return reader -> {
                try {
                    argumentType.parse(reader);
                    return true;
                } catch (CommandSyntaxException ignored) {
                    return false;
                }
            };
        }

        static Exclusion forFlag(Flag<?> flag) {
            return reader -> {
                String str = reader.readUnquotedString();
                return str.equals(flag.getShortFlag()) || str.equals(flag.getFlag());
            };
        }
    }
}
