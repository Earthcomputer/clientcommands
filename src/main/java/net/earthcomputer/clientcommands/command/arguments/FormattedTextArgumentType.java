package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FormattedTextArgumentType implements ArgumentType<LiteralText> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Earth", "bold{xpple}", "bold{italic{red{nwex}}}");

    private static final DynamicCommandExceptionType INVALID_ARGUMENT_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.client.invalidArgument", arg));

    private FormattedTextArgumentType() {
    }

    public static FormattedTextArgumentType formattedText() {
        return new FormattedTextArgumentType();
    }

    public static LiteralText getFormattedText(CommandContext<ServerCommandSource> context, String arg) {
        return context.getArgument(arg, LiteralText.class);
    }

    @Override
    public LiteralText parse(StringReader reader) throws CommandSyntaxException {
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

        public LiteralText parse() throws CommandSyntaxException {
            int cursor = reader.getCursor();
            suggestor = builder -> {
                SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                CommandSource.suggestMatching(FormattedText.FORMATTING.keySet(), newBuilder);
                builder.add(newBuilder);
            };

            String word = reader.readUnquotedString();

            if (FormattedText.FORMATTING.containsKey(word.toLowerCase(Locale.ROOT))) {
                FormattedText.Styler styler = FormattedText.FORMATTING.get(word.toLowerCase(Locale.ROOT));
                suggestor = null;
                reader.skipWhitespace();

                if (!reader.canRead() || reader.peek() != '{') {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, "{");
                }
                reader.skip();
                reader.skipWhitespace();
                LiteralText literalText;
                List<String> arguments = new ArrayList<>();
                if (reader.canRead()) {
                    if (reader.peek() != '}') {
                        if (StringReader.isQuotedStringStart(reader.peek())) {
                            literalText = new LiteralText(reader.readQuotedString());
                        } else {
                            literalText = parse();
                        }
                        reader.skipWhitespace();
                        while (reader.canRead() && reader.peek() != '}') {
                            if (arguments.isEmpty()) {
                                suggestor = builder -> {
                                    SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                                    CommandSource.suggestMatching(styler.suggestions, newBuilder);
                                    builder.add(newBuilder);
                                };
                            }
                            if (reader.peek() != ',') {
                                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, ",");
                            }
                            reader.skip();
                            reader.skipWhitespace();
                            arguments.add(readArgument());
                            reader.skipWhitespace();
                        }
                    } else {
                        literalText = new LiteralText("");
                    }
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(reader, "}");
                }
                reader.skip();

                if (styler.argumentCount != arguments.size()) {
                    reader.setCursor(cursor);
                    reader.readUnquotedString();
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
                }
                return new FormattedText(styler.operator, literalText, arguments).style();
            } else {
                return new LiteralText(word + readArgument());
            }
        }

        private String readArgument() {
            final int start = reader.getCursor();
            while (reader.canRead() && isAllowedInArgument(reader.peek())) {
                reader.skip();
            }
            return reader.getString().substring(start, reader.getCursor());
        }

        private static boolean isAllowedInArgument(final char c) {
            return c != ',' && c != '{' && c != '}';
        }
    }

    static class FormattedText {

        private static final Map<String, Styler> FORMATTING = ImmutableMap.<String, Styler>builder()
                .put("aqua", new Styler((s, o) -> s.withFormatting(Formatting.AQUA), 0))
                .put("black", new Styler((s, o) -> s.withFormatting(Formatting.BLACK), 0))
                .put("blue", new Styler((s, o) -> s.withFormatting(Formatting.BLUE), 0))
                .put("bold", new Styler((s, o) -> s.withFormatting(Formatting.BOLD), 0))
                .put("dark_aqua", new Styler((s, o) -> s.withFormatting(Formatting.DARK_AQUA), 0))
                .put("dark_blue", new Styler((s, o) -> s.withFormatting(Formatting.DARK_BLUE), 0))
                .put("dark_gray", new Styler((s, o) -> s.withFormatting(Formatting.DARK_GRAY), 0))
                .put("dark_green", new Styler((s, o) -> s.withFormatting(Formatting.DARK_GREEN), 0))
                .put("dark_purple", new Styler((s, o) -> s.withFormatting(Formatting.DARK_PURPLE), 0))
                .put("dark_red", new Styler((s, o) -> s.withFormatting(Formatting.DARK_RED), 0))
                .put("gold", new Styler((s, o) -> s.withFormatting(Formatting.GOLD), 0))
                .put("gray", new Styler((s, o) -> s.withFormatting(Formatting.GRAY), 0))
                .put("green", new Styler((s, o) -> s.withFormatting(Formatting.GREEN), 0))
                .put("italic", new Styler((s, o) -> s.withFormatting(Formatting.ITALIC), 0))
                .put("light_purple", new Styler((s, o) -> s.withFormatting(Formatting.LIGHT_PURPLE), 0))
                .put("obfuscated", new Styler((s, o) -> s.withFormatting(Formatting.OBFUSCATED), 0))
                .put("red", new Styler((s, o) -> s.withFormatting(Formatting.RED), 0))
                .put("reset", new Styler((s, o) -> s.withFormatting(Formatting.RESET), 0))
                .put("strikethrough", new Styler((s, o) -> s.withFormatting(Formatting.STRIKETHROUGH), 0))
                .put("underline", new Styler((s, o) -> s.withFormatting(Formatting.UNDERLINE), 0))
                .put("white",  new Styler((s, o) -> s.withFormatting(Formatting.WHITE), 0))
                .put("yellow", new Styler((s, o) -> s.withFormatting(Formatting.YELLOW), 0))

                .put("font", new Styler((s, o) -> s.withFont(Identifier.tryParse(o.get(0))), 1, "alt", "default"))
                .put("hex", new Styler((s, o) -> s.withColor(TextColor.fromRgb(Integer.parseInt(o.get(0), 16))), 1))
                .put("insert", new Styler((s, o) -> s.withInsertion(o.get(0)), 1))

                .put("click", new Styler((s, o) -> s.withClickEvent(new ClickEvent(ClickEvent.Action.byName(o.get(0)), o.get(1))), 2, "change_page", "copy_to_clipboard", "open_file", "open_url", "run_command", "suggest_command"))
                .put("hover", new Styler((s, o) -> s.withHoverEvent(HoverEvent.Action.byName(o.get(0)).buildHoverEvent(Text.of(o.get(1)))), 2, "show_entity", "show_item", "show_text"))

                // aliases
                .put("strike", new Styler((s, o) -> s.withFormatting(Formatting.STRIKETHROUGH), 0))
                .put("magic", new Styler((s, o) -> s.withFormatting(Formatting.OBFUSCATED), 0))
                .build();

        private final BiFunction<Style, List<String>, Style> styler;
        private final LiteralText argument;
        private final List<String> optional;

        public FormattedText(BiFunction<Style, List<String>, Style> styler, LiteralText argument, List<String> optional) {
            this.styler = styler;
            this.argument = argument;
            this.optional = optional;
        }

        public LiteralText style() {
            return (LiteralText) this.argument.setStyle(this.styler.apply(this.argument.getStyle(), this.optional));
        }

        private static final class Styler {

            private final BiFunction<Style, List<String>, Style> operator;
            private final int argumentCount;
            private final String[] suggestions;

            private Styler(BiFunction<Style, List<String>, Style> operator, int argumentCount, String... suggestions) {
                this.operator = operator;
                this.argumentCount = argumentCount;
                this.suggestions = suggestions;
            }
        }
    }
}
