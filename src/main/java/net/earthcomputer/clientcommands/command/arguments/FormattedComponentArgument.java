package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import net.earthcomputer.clientcommands.mixin.HoverEventActionAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class FormattedComponentArgument implements ArgumentType<MutableComponent> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Earth", "bold{xpple}", "bold{italic{red{nwex}}}");
    private static final DynamicCommandExceptionType INVALID_CLICK_ACTION = new DynamicCommandExceptionType(action -> Component.translatable("commands.client.invalidClickAction", action));
    private static final DynamicCommandExceptionType INVALID_HOVER_ACTION = new DynamicCommandExceptionType(action -> Component.translatable("commands.client.invalidHoverAction", action));
    private static final DynamicCommandExceptionType INVALID_HOVER_EVENT = new DynamicCommandExceptionType(event -> Component.translatable("commands.client.invalidHoverEvent", event));

    private FormattedComponentArgument() {
    }

    public static FormattedComponentArgument formattedComponent() {
        return new FormattedComponentArgument();
    }

    public static MutableComponent getFormattedComponent(CommandContext<FabricClientCommandSource> context, String arg) {
        return context.getArgument(arg, MutableComponent.class);
    }

    @Override
    public MutableComponent parse(StringReader reader) throws CommandSyntaxException {
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

        public MutableComponent parse() throws CommandSyntaxException {
            int cursor = reader.getCursor();
            suggestor = builder -> {
                SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                SharedSuggestionProvider.suggest(FormattedText.FORMATTING.keySet(), newBuilder);
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
                MutableComponent literalText;
                List<String> arguments = new ArrayList<>();
                if (reader.canRead()) {
                    if (reader.peek() != '}') {
                        if (StringReader.isQuotedStringStart(reader.peek())) {
                            literalText = Component.literal(reader.readQuotedString());
                        } else {
                            literalText = parse();
                        }
                        reader.skipWhitespace();
                        while (reader.canRead() && reader.peek() != '}') {
                            if (arguments.isEmpty()) {
                                suggestor = builder -> {
                                    SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                                    SharedSuggestionProvider.suggest(styler.suggestions, newBuilder);
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
                        literalText = Component.literal("");
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
                return Component.literal(word + readArgument());
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
                .put("aqua", new Styler((s, o) -> s.applyFormat(ChatFormatting.AQUA), 0))
                .put("black", new Styler((s, o) -> s.applyFormat(ChatFormatting.BLACK), 0))
                .put("blue", new Styler((s, o) -> s.applyFormat(ChatFormatting.BLUE), 0))
                .put("bold", new Styler((s, o) -> s.applyFormat(ChatFormatting.BOLD), 0))
                .put("dark_aqua", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_AQUA), 0))
                .put("dark_blue", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_BLUE), 0))
                .put("dark_gray", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_GRAY), 0))
                .put("dark_green", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_GREEN), 0))
                .put("dark_purple", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_PURPLE), 0))
                .put("dark_red", new Styler((s, o) -> s.applyFormat(ChatFormatting.DARK_RED), 0))
                .put("gold", new Styler((s, o) -> s.applyFormat(ChatFormatting.GOLD), 0))
                .put("gray", new Styler((s, o) -> s.applyFormat(ChatFormatting.GRAY), 0))
                .put("green", new Styler((s, o) -> s.applyFormat(ChatFormatting.GREEN), 0))
                .put("italic", new Styler((s, o) -> s.applyFormat(ChatFormatting.ITALIC), 0))
                .put("light_purple", new Styler((s, o) -> s.applyFormat(ChatFormatting.LIGHT_PURPLE), 0))
                .put("obfuscated", new Styler((s, o) -> s.applyFormat(ChatFormatting.OBFUSCATED), 0))
                .put("red", new Styler((s, o) -> s.applyFormat(ChatFormatting.RED), 0))
                .put("reset", new Styler((s, o) -> s.applyFormat(ChatFormatting.RESET), 0))
                .put("strikethrough", new Styler((s, o) -> s.applyFormat(ChatFormatting.STRIKETHROUGH), 0))
                .put("underline", new Styler((s, o) -> s.applyFormat(ChatFormatting.UNDERLINE), 0))
                .put("white",  new Styler((s, o) -> s.applyFormat(ChatFormatting.WHITE), 0))
                .put("yellow", new Styler((s, o) -> s.applyFormat(ChatFormatting.YELLOW), 0))

                .put("font", new Styler((s, o) -> s.withFont(ResourceLocation.tryParse(o.getFirst())), 1, "alt", "default"))
                .put("hex", new Styler((s, o) -> s.withColor(TextColor.fromRgb(Integer.parseInt(o.getFirst(), 16))), 1))
                .put("insert", new Styler((s, o) -> s.withInsertion(o.getFirst()), 1))

                .put("click", new Styler((s, o) -> s.withClickEvent(parseClickEvent(o.getFirst(), o.get(1))), 2, "change_page", "copy_to_clipboard", "open_file", "open_url", "run_command", "suggest_command"))
                .put("hover", new Styler((s, o) -> s.withHoverEvent(parseHoverEvent(o.getFirst(), o.get(1))), 2, "show_entity", "show_item", "show_text"))

                // aliases
                .put("strike", new Styler((s, o) -> s.applyFormat(ChatFormatting.STRIKETHROUGH), 0))
                .put("magic", new Styler((s, o) -> s.applyFormat(ChatFormatting.OBFUSCATED), 0))
                .build();

        private final StylerFunc styler;
        private final MutableComponent argument;
        private final List<String> args;

        public FormattedText(StylerFunc styler, MutableComponent argument, List<String> args) {
            this.styler = styler;
            this.argument = argument;
            this.args = args;
        }

        public MutableComponent style() throws CommandSyntaxException {
            return this.argument.setStyle(this.styler.apply(this.argument.getStyle(), this.args));
        }

        private record Styler(StylerFunc operator, int argumentCount, String... suggestions) {}

        @FunctionalInterface
        interface StylerFunc {
            Style apply(Style style, List<String> args) throws CommandSyntaxException;
        }

        private static final Function<String, ClickEvent.Action> CLICK_EVENT_ACTION_BY_NAME = StringRepresentable.createNameLookup(ClickEvent.Action.values(), Function.identity());

        private static ClickEvent parseClickEvent(String name, String value) throws CommandSyntaxException {
            ClickEvent.Action action = CLICK_EVENT_ACTION_BY_NAME.apply(name);
            if (action == null) {
                throw INVALID_CLICK_ACTION.create(name);
            }
            return new ClickEvent(action, value);
        }

        private static HoverEvent parseHoverEvent(String name, String value) throws CommandSyntaxException {
            HoverEvent.Action<?> action = HoverEvent.Action.UNSAFE_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name)).result().orElse(null);
            if (action == null) {
                throw INVALID_HOVER_ACTION.create(name);
            }

            JsonElement component = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, Component.nullToEmpty(value)).getOrThrow();
            HoverEvent.TypedHoverEvent<?> eventData = ((HoverEventActionAccessor) action).getLegacyCodec().codec().parse(JsonOps.INSTANCE, component).getOrThrow(error -> INVALID_HOVER_EVENT.create(value));
            return new HoverEvent(eventData);
        }
    }
}
