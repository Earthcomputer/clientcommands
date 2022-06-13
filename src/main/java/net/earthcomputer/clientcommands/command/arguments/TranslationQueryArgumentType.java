package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TranslationQueryArgumentType implements ArgumentType<TranslationQueryArgumentType.TranslationQuery> {

    private static final Collection<String> EXAMPLES = Arrays.asList("\"Voorbeeld\" from nl to en", "\"Non numeranda, sed ponderanda sunt argumenta\" from la", "\"Cogito, ergo sum\"");

    private static final DynamicCommandExceptionType UNKNOWN_LANGUAGE_CODE_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.ctranslate.unknownLanguageCode", arg));
    private static final SimpleCommandExceptionType EXPECTED_FROM_TO_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ctranslate.expectedFromTo"));

    private static final Collection<String> languages;

    static {
        // https://cloud.google.com/translate/docs/languages
        // maybe automate this somehow? (hence the static block)
        languages = Arrays.asList("af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca", "ceb", "zh-CN", "zh", "zh-TW", "co", "hr", "cs", "da", "nl", "en", "eo", "et", "fi", "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha", "haw", "he", "iw", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja", "jv", "kn", "kk", "km", "rw", "ko", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", "ny", "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd", "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt", "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu");
    }

    public static TranslationQueryArgumentType translationQuery() {
        return new TranslationQueryArgumentType();
    }

    public static TranslationQuery getTranslationQuery(CommandContext<FabricClientCommandSource> context, String arg) {
        TranslationQuery query = context.getArgument(arg, TranslationQuery.class);
        return new TranslationQuery(query.from() == null ? "auto" : query.from(), query.to() == null ? context.getSource().getClient().options.language : query.to(), query.query());
    }

    @Override
    public TranslationQuery parse(StringReader reader) throws CommandSyntaxException {
        return new Parser(reader).parse();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());

        Parser parser = new Parser(reader);

        try {
            parser.parse();
        } catch (CommandSyntaxException ignored) {
        }

        if (parser.suggestor != null) {
            parser.suggestor.accept(builder);
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static class Parser {
        private final StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        public Parser(StringReader reader) {
            this.reader = reader;
        }

        public TranslationQuery parse() throws CommandSyntaxException {
            String query = reader.readQuotedString();
            String from = null;
            String to = null;
            while (reader.canRead()) {
                reader.skipWhitespace();
                Option option = parseFromTo();
                boolean suggest = reader.canRead();
                reader.skipWhitespace();
                String language = parseLanguage(suggest);
                if (option == Option.FROM) {
                    from = language;
                } else {
                    to = language;
                }
            }
            return new TranslationQuery(from, to, query);
        }

        private Option parseFromTo() throws CommandSyntaxException {
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                CommandSource.suggestMatching(new String[]{"from", "to"}, builder);
                suggestions.add(builder);
            };
            String option = reader.readUnquotedString();
            return switch (option) {
                case "from" -> Option.FROM;
                case "to" -> Option.TO;
                default -> throw EXPECTED_FROM_TO_EXCEPTION.create();
            };
        }

        private String parseLanguage(boolean suggest) throws CommandSyntaxException {
            int start = reader.getCursor();
            if (suggest) {
                suggestor = suggestions -> {
                    SuggestionsBuilder builder = suggestions.createOffset(start);
                    CommandSource.suggestMatching(languages, builder);
                    suggestions.add(builder);
                };
            }
            String language = reader.readUnquotedString();
            if (languages.contains(language)) {
                return language;
            }
            throw UNKNOWN_LANGUAGE_CODE_EXCEPTION.create(language);
        }

        private enum Option {
            FROM,
            TO
        }
    }

    public record TranslationQuery(String from, String to, String query) {
    }
}
