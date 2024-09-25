package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class ListArgument<T, U extends ArgumentType<T>> implements ArgumentType<List<T>> {

    private static final SimpleCommandExceptionType TOO_FEW_ARGUMENTS_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.tooFewArguments"));

    private final U argumentType;
    private final int min;
    private final int max;

    private ListArgument(U argumentType, int min, int max) {
        this.argumentType = argumentType;
        this.min = min;
        this.max = max;
    }

    public static <T, U extends ArgumentType<T>> ListArgument<T, U> list(U argumentType) {
        return new ListArgument<>(argumentType, 1, Integer.MAX_VALUE);
    }

    public static <T, U extends ArgumentType<T>> ListArgument<T, U> list(U argumentType, int min) {
        return new ListArgument<>(argumentType, min, Integer.MAX_VALUE);
    }

    public static <T, U extends ArgumentType<T>> ListArgument<T, U> list(U argumentType, int min, int max) {
        return new ListArgument<>(argumentType, min, max);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(final CommandContext<?> context, final String name) {
        return (List<T>) context.getArgument(name, List.class);
    }

    @Override
    public List<T> parse(StringReader reader) throws CommandSyntaxException {
        List<T> parsedArguments = new ArrayList<>();
        int cursor = reader.getCursor();
        int readAmount = 0;
        try {
            while (reader.canRead() && readAmount < this.max) {
                cursor = reader.getCursor();
                parsedArguments.add(this.argumentType.parse(reader));
                readAmount++;
                // read in the separator
                if (reader.canRead()) {
                    reader.expect(' ');
                }
            }
        } catch (CommandSyntaxException e) {
            if (readAmount < this.min) {
                throw e;
            }
            reader.setCursor(cursor);
        }
        if (readAmount < this.min) {
            throw TOO_FEW_ARGUMENTS_EXCEPTION.create();
        }
        return parsedArguments;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        int readAmount = 0;
        int cursor = reader.getCursor();
        try {
            while (reader.canRead() && readAmount < this.max - 1) {
                this.argumentType.parse(reader);
                readAmount++;
                // read in the separator
                if (reader.canRead()) {
                    reader.expect(' ');
                    cursor = reader.getCursor();
                }
            }
        } catch (CommandSyntaxException ignored) {
        }
        return this.argumentType.listSuggestions(context, builder.createOffset(cursor));
    }

    @Override
    public Collection<String> getExamples() {
        Collection<String> elementExamples = argumentType.getExamples();
        if (elementExamples.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> elementExamplesList;
        if (elementExamples instanceof List<String> lst) {
            elementExamplesList = lst;
        } else {
            elementExamplesList = new ArrayList<>(elementExamples);
        }

        Random rand = new Random(0);
        String[] ret = new String[3];
        for (int i = 0; i < 3; i++) {
            StringBuilder sb = new StringBuilder();
            int times = min + rand.nextInt(Math.min(min + 10, max) - min + 1);
            for (int j = 0; j < times; j++) {
                if (j != 0) {
                    sb.append(' ');
                }
                sb.append(elementExamplesList.get(rand.nextInt(elementExamples.size())));
            }
            ret[i] = sb.toString();
        }
        return Arrays.asList(ret);
    }
}
