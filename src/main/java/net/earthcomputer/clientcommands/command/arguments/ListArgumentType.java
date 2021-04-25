package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ListArgumentType<T, U extends ArgumentType<T>> implements ArgumentType<List<T>> {

    private static final SimpleCommandExceptionType TOO_FEW_ARGUMENTS_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.client.tooFewArguments"));

    private final U argumentType;
    private final int min;
    private final int max;

    private ListArgumentType(U argumentType, int min, int max) {
        this.argumentType = argumentType;
        this.min = min;
        this.max = max;
    }

    public static <T, U extends ArgumentType<T>> ListArgumentType<T, U> list(U argumentType) {
        return new ListArgumentType<>(argumentType, 1, Integer.MAX_VALUE);
    }

    public static <T, U extends ArgumentType<T>> ListArgumentType<T, U> list(U argumentType, int min) {
        return new ListArgumentType<>(argumentType, min, Integer.MAX_VALUE);
    }

    public static <T, U extends ArgumentType<T>> ListArgumentType<T, U> list(U argumentType, int min, int max) {
        return new ListArgumentType<>(argumentType, min, max);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(final CommandContext<?> context, final String name) {
        return (List<T>) context.getArgument(name, List.class);
    }

    /**
     * @author CreepyCre
     */
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
                // TODO: 25-4-2021 implement better separator reading
                if (reader.canRead()) {
                    reader.read();
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

    /**
     * @author CreepyCre
     */
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
                // TODO: 25-4-2021 implement better separator reading
                if (reader.canRead()) {
                    reader.read();
                    cursor = reader.getCursor();
                }
            }
        } catch (CommandSyntaxException ignored) {
        }
        // TODO: 25-4-2021 implement better separators
        return this.argumentType.listSuggestions(context, builder.createOffset(cursor));
    }

    /**
     * @author CreepyCre
     */
    @Override
    public Collection<String> getExamples() {
        return this.argumentType.getExamples();
    }
}
