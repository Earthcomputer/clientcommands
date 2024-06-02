package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class CombinedArgument<A, B> implements ArgumentType<CombinedArgument.Combined<A, B>> {
    ArgumentType<A> firstArgument;
    ArgumentType<B> secondArgument;
    private static final SimpleCommandExceptionType TOO_FEW_ARGUMENTS_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.tooFewArguments"));

    private CombinedArgument(ArgumentType<A> first, ArgumentType<B> second) {
        firstArgument = first;
        secondArgument = second;
    }

    public static <A, B> CombinedArgument<A, B> combined(ArgumentType<A> first, ArgumentType<B> second) {
        return new CombinedArgument<>(first, second);
    }

    public static <A, B, S> Combined<A, B> getCombined(CommandContext<S> context, String name) {
        return context.getArgument(name, Combined.class);
    }

    @Override
    public Combined<A, B> parse(StringReader reader) throws CommandSyntaxException {
        A first = firstArgument.parse(reader);
        if(!reader.canRead()) throw TOO_FEW_ARGUMENTS_EXCEPTION.create();
        reader.expect(' ');
        if(!reader.canRead()) throw TOO_FEW_ARGUMENTS_EXCEPTION.create();
        B second = secondArgument.parse(reader);
        return new Combined<>(first, second);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        int readAmount = 0;
        int cursor = reader.getCursor();
        try {
            if(reader.canRead()) {
                firstArgument.parse(reader);
                if(reader.canRead()) {
                    reader.expect(' ');
                    readAmount++;
                    cursor = reader.getCursor();
                    secondArgument.parse(reader);
                }
            }
        } catch (CommandSyntaxException ignored) {
        }
        if(readAmount == 0) {
            return firstArgument.listSuggestions(context, builder.createOffset(cursor));
        } else {
            return secondArgument.listSuggestions(context, builder.createOffset(cursor));
        }
    }

    public record Combined<A, B>(A first, B second) {
    }
}
