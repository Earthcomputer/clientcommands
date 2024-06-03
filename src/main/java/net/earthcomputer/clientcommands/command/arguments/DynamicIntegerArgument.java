package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class DynamicIntegerArgument implements ArgumentType<Integer> {
    Supplier<Integer> low;
    Supplier<Integer> high;
    boolean allowAny = false;

    DynamicIntegerArgument(Supplier<Integer> low, Supplier<Integer> high) {
        this.low = low;
        this.high = high;
    }

    public DynamicIntegerArgument allowAny() {
        allowAny = true;
        return this;
    }

    public static <S> int getInteger(CommandContext<S> context, String name) {
        return context.getArgument(name, int.class);
    }

    public static DynamicIntegerArgument integer(Supplier<Integer> low, Supplier<Integer> high) {
        return new DynamicIntegerArgument(low, high);
    }

    public static DynamicIntegerArgument integer(int low, Supplier<Integer> high) {
        return integer(() -> low, high);
    }
    public static DynamicIntegerArgument integer(Supplier<Integer> low, int high) {
        return integer(low, () -> high);
    }

    public static DynamicIntegerArgument integer(Supplier<Integer> limit) {
        return integer(0, limit);
    }

    @Override
    public Integer parse(final StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        if(allowAny && reader.canRead() && reader.peek() == '*') {
            reader.read();
            return Integer.MAX_VALUE;
        }
        final int result = reader.readInt();

        var min = low.get();
        var max = high.get();

        if (result < min) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, result, min);
        }
        if (result > max) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().createWithContext(reader, result, max);
        }
        return result;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if(allowAny) {
            builder.suggest("*");
        }
        return builder.buildFuture();
    }
}