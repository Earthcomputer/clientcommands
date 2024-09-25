package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexArgument implements ArgumentType<Pattern> {

    private static final DynamicCommandExceptionType EXPECTED_REGEX_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.client.expectedRegex", arg));

    private final RegexType type;

    private RegexArgument(RegexType type) {
        this.type = type;
    }

    public static RegexArgument wordRegex() {
        return new RegexArgument(RegexType.SINGLE_WORD);
    }

    public static RegexArgument slashyRegex() {
        return new RegexArgument(RegexType.SLASHY_PHRASE);
    }

    public static RegexArgument greedyRegex() {
        return new RegexArgument(RegexType.GREEDY_PHRASE);
    }

    public static Pattern getRegex(CommandContext<?> context, String name) {
        return context.getArgument(name, Pattern.class);
    }

    @Override
    public Pattern parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        if (type == RegexType.GREEDY_PHRASE) {
            String text = reader.getRemaining();
            try {
                Pattern pattern = Pattern.compile(text);
                reader.setCursor(reader.getTotalLength());
                return pattern;
            } catch (PatternSyntaxException e) {
                reader.setCursor(start);
                throw EXPECTED_REGEX_EXCEPTION.createWithContext(reader, text);
            }
        } else if (type == RegexType.SINGLE_WORD) {
            String text = reader.readUnquotedString();
            try {
                return Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                reader.setCursor(start);
                throw EXPECTED_REGEX_EXCEPTION.createWithContext(reader, text);
            }
        } else {
            return parseSlashyRegex(reader);
        }
    }

    public static Pattern parseSlashyRegex(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();

        boolean slashy = reader.canRead() && reader.peek() == '/';
        if (!slashy) {
            String text = reader.readUnquotedString();
            try {
                return Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                reader.setCursor(start);
                throw EXPECTED_REGEX_EXCEPTION.createWithContext(reader, text);
            }
        }

        reader.skip(); // /

        StringBuilder regex = new StringBuilder();
        boolean escaped = false;
        while (true) {
            if (!reader.canRead()) {
                reader.setCursor(start);
                throw EXPECTED_REGEX_EXCEPTION.createWithContext(reader, reader.getString().substring(start));
            }

            if (reader.peek() == '/') {
                if (!escaped) {
                    reader.skip();
                    try {
                        return Pattern.compile(regex.toString());
                    } catch (PatternSyntaxException e) {
                        int end = reader.getCursor();
                        reader.setCursor(start);
                        throw EXPECTED_REGEX_EXCEPTION.createWithContext(reader, reader.getString().substring(start, end));
                    }
                } else {
                    regex.deleteCharAt(regex.length() - 1); // the backslash which escaped this slash
                }
            }

            escaped = reader.peek() == '\\' && !escaped;
            regex.append(reader.peek());
            reader.skip();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return type.getExamples();
    }

    public enum RegexType {
        SINGLE_WORD("word", "\\w+"),
        SLASHY_PHRASE("/\\w+/", "word", "//"),
        GREEDY_PHRASE("word", "words with spaces", "/and symbols/"),;

        private final Collection<String> examples;

        RegexType(final String... examples) {
            this.examples = Arrays.asList(examples);
        }

        public Collection<String> getExamples() {
            return examples;
        }
    }
}
