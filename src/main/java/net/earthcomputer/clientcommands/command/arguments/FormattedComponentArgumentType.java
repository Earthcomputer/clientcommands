package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FormattedComponentArgumentType implements ArgumentType<MutableComponent> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Earth", "&lxpple", "&l&o#fb8919nwex");

    private static final SimpleCommandExceptionType EXPECTED_FORMATTING_CODE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.expectedFormattingCode"));
    private static final SimpleCommandExceptionType UNKNOWN_FORMATTING_CODE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.unknownFormattingCode"));
    private static final SimpleCommandExceptionType EXPECTED_HEX_VALUE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.expectedHexValue"));
    private static final SimpleCommandExceptionType INVALID_HEX_VALUE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.client.invalidHexValue"));

    public static FormattedComponentArgumentType formattedComponent() {
        return new FormattedComponentArgumentType();
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
        } catch (CommandSyntaxException ignore) {
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
            MutableComponent text = Component.empty();
            Style style = Style.EMPTY;
            while (reader.canRead()) {
                char c = reader.read();
                if (c == '&') { // ChatFormatting.PREFIX_CODE is not writable in chat
                    suggestor = suggestions -> {
                        SuggestionsBuilder builder = suggestions.createOffset(reader.getCursor());
                        SharedSuggestionProvider.suggest(Arrays.stream(ChatFormatting.values()).map(f -> String.valueOf(f.getChar())), builder);
                        suggestions.add(builder);
                    };
                    if (!reader.canRead()) {
                        throw EXPECTED_FORMATTING_CODE_EXCEPTION.create();
                    }
                    char code = reader.read();
                    ChatFormatting formatting = ChatFormatting.getByCode(code);
                    if (formatting == null) {
                        throw UNKNOWN_FORMATTING_CODE_EXCEPTION.create();
                    }
                    style = style.applyFormat(formatting);
                } else if (c == TextColor.CUSTOM_COLOR_PREFIX.charAt(0)) {
                    if (!reader.canRead()) {
                        throw EXPECTED_HEX_VALUE_EXCEPTION.create();
                    }
                    StringBuilder builder = new StringBuilder();
                    int hex = -1;
                    for (int i = 0; i < 6 && reader.canRead(); i++) {
                        builder.append(reader.peek());
                        try {
                            hex = Integer.parseInt(builder.toString(), 16);
                        } catch (NumberFormatException e) {
                            break;
                        }
                        reader.skip();
                    }
                    if (hex == -1) {
                        throw INVALID_HEX_VALUE_EXCEPTION.create();
                    }
                    style = style.withColor(hex);
                } else {
                    text.append(Component.literal(String.valueOf(c)).setStyle(style));
                }
                suggestor = null;
            }
            return text;
        }
    }
}
