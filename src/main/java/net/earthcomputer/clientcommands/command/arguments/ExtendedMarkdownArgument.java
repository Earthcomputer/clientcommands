package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
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

public class ExtendedMarkdownArgument implements ArgumentType<MutableComponent> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Earth", "bold{xpple}", "red{hello blue{world}!}", "*italic*");
    private static final SimpleCommandExceptionType TOO_DEEPLY_NESTED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.componentTooDeeplyNested"));
    private static final DynamicCommandExceptionType INVALID_CLICK_ACTION_EXCEPTION = new DynamicCommandExceptionType(action -> Component.translatable("commands.client.invalidClickAction", action));
    private static final DynamicCommandExceptionType INVALID_HOVER_ACTION_EXCEPTION = new DynamicCommandExceptionType(action -> Component.translatable("commands.client.invalidHoverAction", action));
    private static final DynamicCommandExceptionType INVALID_HOVER_EVENT_EXCEPTION = new DynamicCommandExceptionType(event -> Component.translatable("commands.client.invalidHoverEvent", event));

    private ExtendedMarkdownArgument() {
    }

    public static ExtendedMarkdownArgument extendedMarkdown() {
        return new ExtendedMarkdownArgument();
    }

    public static MutableComponent getExtendedMarkdown(CommandContext<FabricClientCommandSource> context, String arg) {
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
        private static final int MAX_NESTING = 50;

        private final StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        public Parser(StringReader reader) {
            this.reader = reader;
        }

        public MutableComponent parse() throws CommandSyntaxException {
            return parse(reader.getTotalLength(), 0);
        }

        private MutableComponent parse(int end, int depth) throws CommandSyntaxException {
            if (depth > MAX_NESTING) {
                throw TOO_DEEPLY_NESTED_EXCEPTION.createWithContext(reader);
            }

            StringBuilder plainText = new StringBuilder();
            List<MutableComponent> components = new ArrayList<>();
            while (reader.getCursor() < end) {
                int cursor = reader.getCursor();
                suggestor = builder -> {
                    SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                    SharedSuggestionProvider.suggest(FormattedCode.CODES.keySet().stream().map(str -> str + '{'), newBuilder);
                    builder.add(newBuilder);
                };

                String word = readWordNotSurroundedByUnderscore();
                if (!word.isEmpty() && reader.canRead() && reader.peek() == '{') {
                    reader.skip();
                    word = word.toLowerCase(Locale.ROOT);

                    // convert legacy formatting code into modern name
                    if (word.length() == 1) {
                        ChatFormatting legacyFormatting = ChatFormatting.getByCode(word.charAt(0));
                        if (legacyFormatting != null && legacyFormatting != ChatFormatting.RESET) {
                            word = legacyFormatting.getName().toLowerCase(Locale.ROOT);
                        }
                    }

                    FormattedCode.Styler styler = FormattedCode.CODES.get(word);
                    if (styler != null) {
                        int innerStart = reader.getCursor();
                        int braceCount = 1;
                        while (braceCount > 0) {
                            int openIndex = findUnescaped('{', end);
                            int closeIndex = findUnescaped('}', end);
                            if (closeIndex == end) {
                                break;
                            }
                            if (openIndex < closeIndex) {
                                braceCount++;
                                reader.setCursor(openIndex + 1);
                            } else {
                                braceCount--;
                                reader.setCursor(closeIndex + 1);
                            }
                        }
                        int innerEnd = braceCount == 0 ? reader.getCursor() - 1 : end;
                        reader.setCursor(innerStart);
                        List<String> arguments = new ArrayList<>(styler.argumentCount());
                        if (styler.argumentCount() > 0) {
                            reader.skipWhitespace();
                            int argStart = reader.getCursor();
                            suggestor = builder -> {
                                SuggestionsBuilder newBuilder = builder.createOffset(argStart);
                                SharedSuggestionProvider.suggest(styler.suggestions(), newBuilder);
                                builder.add(newBuilder);
                            };
                            arguments.add(readArgument());
                            reader.skipWhitespace();
                            reader.expect(',');
                            reader.skipWhitespace();
                            for (int i = 1; i < styler.argumentCount(); i++) {
                                suggestor = SuggestionsBuilder::buildFuture;
                                arguments.add(readArgument());
                                reader.skipWhitespace();
                                reader.expect(',');
                                reader.skipWhitespace();
                            }
                        }

                        MutableComponent innerComponent = parse(innerEnd, depth + 1);
                        reader.expect('}');
                        innerComponent.withStyle(styler.operator().apply(innerComponent.getStyle(), arguments));

                        if (!plainText.isEmpty()) {
                            components.add(Component.literal(plainText.toString()));
                            plainText.setLength(0);
                        }
                        components.add(innerComponent);
                        continue;
                    }
                }

                plainText.append(word);

                if (reader.getCursor() >= end) {
                    break;
                }

                char ch = reader.read();
                switch (ch) {
                    case '~' -> {
                        if (reader.getCursor() < end && reader.peek() == '~') {
                            reader.skip();
                            MutableComponent innerComponent = parse(findUnescaped("~~", end), depth + 1)
                                .withStyle(style -> style.withStrikethrough(true));
                            reader.expect('~');
                            reader.expect('~');
                            if (!plainText.isEmpty()) {
                                components.add(Component.literal(plainText.toString()));
                                plainText.setLength(0);
                            }
                            components.add(innerComponent);
                        } else {
                            plainText.append('~');
                        }
                    }
                    case '*' -> {
                        if (reader.getCursor() < end && reader.peek() == '*') {
                            reader.skip();
                            MutableComponent innerComponent = parse(findUnescaped("**", end), depth + 1)
                                .withStyle(style -> style.withBold(true));
                            reader.expect('*');
                            reader.expect('*');
                            if (!plainText.isEmpty()) {
                                components.add(Component.literal(plainText.toString()));
                                plainText.setLength(0);
                            }
                            components.add(innerComponent);
                        } else {
                            MutableComponent innerComponent = parse(findUnescaped('*', end), depth + 1)
                                .withStyle(style -> style.withItalic(true));
                            reader.expect('*');
                            if (!plainText.isEmpty()) {
                                components.add(Component.literal(plainText.toString()));
                                plainText.setLength(0);
                            }
                            components.add(innerComponent);
                        }
                    }
                    case '_' -> {
                        if (reader.getCursor() < end && reader.peek() == '_') {
                            reader.skip();
                            MutableComponent innerComponent = parse(findUnescaped("__", end), depth + 1)
                                .withStyle(style -> style.withUnderlined(true));
                            reader.expect('_');
                            reader.expect('_');
                            if (!plainText.isEmpty()) {
                                components.add(Component.literal(plainText.toString()));
                                plainText.setLength(0);
                            }
                            components.add(innerComponent);
                        } else {
                            MutableComponent innerComponent = parse(findUnescaped('_', end), depth + 1)
                                .withStyle(style -> style.withItalic(true));
                            reader.expect('_');
                            if (!plainText.isEmpty()) {
                                components.add(Component.literal(plainText.toString()));
                                plainText.setLength(0);
                            }
                            components.add(innerComponent);
                        }
                    }
                    case '[' -> {
                        MutableComponent linkComponent = parse(findUnescaped(']', end), depth + 1);
                        reader.expect(']');
                        String linkHref;
                        if (reader.getCursor() < end && reader.peek() == '(') {
                            reader.skip();
                            suggestor = SuggestionsBuilder::build;
                            int hrefEnd = reader.getString().indexOf(')', reader.getCursor(), end);
                            if (hrefEnd == -1) {
                                hrefEnd = end;
                            }
                            linkHref = reader.getString().substring(reader.getCursor(), hrefEnd).trim();
                            reader.setCursor(hrefEnd);
                            reader.expect(')');
                        } else {
                            linkHref = linkComponent.getString();
                        }
                        if (!plainText.isEmpty()) {
                            components.add(Component.literal(plainText.toString()));
                            plainText.setLength(0);
                        }
                        components.add(linkComponent.withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, linkHref))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(linkHref)))
                            .withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)));
                    }
                    case '\\' -> {
                        if (reader.getCursor() < end) {
                            String escapedWord = readWordNotSurroundedByUnderscore();
                            if (!escapedWord.isEmpty()) {
                                plainText.append(escapedWord);
                            } else {
                                plainText.append(reader.read());
                            }
                        } else {
                            plainText.append('\\');
                        }
                    }
                    default -> plainText.append(ch);
                }
            }

            if (!plainText.isEmpty()) {
                components.add(Component.literal(plainText.toString()));
            }

            return switch (components.size()) {
                case 0 -> Component.empty();
                case 1 -> components.getFirst();
                default -> {
                    if (components.getFirst().getStyle().isEmpty()) {
                        for (int i = 1; i < components.size(); i++) {
                            components.getFirst().append(components.get(i));
                        }
                        yield components.getFirst();
                    } else {
                        MutableComponent parent = Component.empty();
                        components.forEach(parent::append);
                        yield parent;
                    }
                }
            };
        }

        private boolean isEscaped(int index) {
            boolean isEscaped = false;
            for (int i = index - 1; i >= 0; i--) {
                if (reader.getString().charAt(i) == '\\') {
                    isEscaped = !isEscaped;
                } else {
                    break;
                }
            }
            return isEscaped;
        }

        private int findUnescaped(char ch, int endIndex) {
            int index = reader.getString().indexOf(ch, reader.getCursor(), endIndex);
            while (index != -1 && isEscaped(index)) {
                index = reader.getString().indexOf(ch, index + 1, endIndex);
            }
            return index == -1 ? endIndex : index;
        }

        private int findUnescaped(String str, int endIndex) {
            int index = reader.getString().indexOf(str, reader.getCursor(), endIndex);
            while (index != -1 && isEscaped(index)) {
                index = reader.getString().indexOf(str, index + 1, endIndex);
            }
            return index;
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

        private String readWordNotSurroundedByUnderscore() {
            int start = reader.getCursor();
            String word = reader.readUnquotedString();

            if (word.startsWith("_")) {
                reader.setCursor(start);
                return "";
            }

            while (word.endsWith("_")) {
                word = word.substring(0, word.length() - 1);
                reader.setCursor(reader.getCursor() - 1);
            }

            return word;
        }
    }

    private static class FormattedCode {
        private static final Map<String, Styler> CODES = ImmutableMap.<String, Styler>builder()
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
                .put("strikethrough", new Styler((s, o) -> s.applyFormat(ChatFormatting.STRIKETHROUGH), 0))
                .put("underline", new Styler((s, o) -> s.applyFormat(ChatFormatting.UNDERLINE), 0))
                .put("white",  new Styler((s, o) -> s.applyFormat(ChatFormatting.WHITE), 0))
                .put("yellow", new Styler((s, o) -> s.applyFormat(ChatFormatting.YELLOW), 0))

                .put("font", new Styler((s, o) -> s.withFont(ResourceLocation.read(new StringReader(o.getFirst()))), 1, "alt", "default"))
                .put("hex", new Styler((s, o) -> s.withColor(TextColor.fromRgb(parseHex(o.getFirst()))), 1))
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

        public FormattedCode(StylerFunc styler, MutableComponent argument, List<String> args) {
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
                throw INVALID_CLICK_ACTION_EXCEPTION.create(name);
            }
            return new ClickEvent(action, value);
        }

        private static HoverEvent parseHoverEvent(String name, String value) throws CommandSyntaxException {
            HoverEvent.Action<?> action = HoverEvent.Action.UNSAFE_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name)).result().orElse(null);
            if (action == null) {
                throw INVALID_HOVER_ACTION_EXCEPTION.create(name);
            }

            JsonElement component = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, Component.nullToEmpty(value)).getOrThrow();
            JsonObject valueJson = new JsonObject();
            valueJson.add("value", component);
            HoverEvent.TypedHoverEvent<?> eventData = action.legacyCodec.codec().parse(JsonOps.INSTANCE, valueJson).getOrThrow(error -> INVALID_HOVER_EVENT_EXCEPTION.create(value));
            return new HoverEvent(eventData);
        }

        private static int parseHex(String hex) throws CommandSyntaxException {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(hex);
            }
        }
    }
}
