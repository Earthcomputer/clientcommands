package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class WithStringArgument<T> implements ArgumentType<WithStringArgument.Result<T>> {

    private final ArgumentType<T> delegate;

    private WithStringArgument(ArgumentType<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> WithStringArgument<T> withString(ArgumentType<T> delegate) {
        return new WithStringArgument<>(delegate);
    }

    @SuppressWarnings("unchecked")
    public static <S, T> Result<T> getWithString(CommandContext<S> context, String arg, Class<T> type) {
        return context.getArgument(arg, Result.class);
    }

    @Override
    public Result<T> parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        T thing = delegate.parse(reader);
        String str = reader.getString().substring(start, reader.getCursor());
        return new Result<>(str, thing);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return delegate.listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return delegate.getExamples();
    }

    public record Result<T>(String string, T value) {}
}
