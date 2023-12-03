package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FormattedTextArgumentType implements ArgumentType<Text> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Earth", "&lxpple", "&l&o#fb8919nwex");

    private static final SimpleCommandExceptionType EXPECTED_FORMATTING_CODE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.client.expectedFormattingCode"));
    private static final SimpleCommandExceptionType UNKNOWN_FORMATTING_CODE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.client.unknownFormattingCode"));
    private static final SimpleCommandExceptionType EXPECTED_HEX_VALUE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.client.expectedHexValue"));
    private static final SimpleCommandExceptionType INVALID_HEX_VALUE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.client.invalidHexValue"));

    public static FormattedTextArgumentType formattedText() {
        return new FormattedTextArgumentType();
    }

    public static Text getFormattedText(CommandContext<FabricClientCommandSource> context, String arg) {
        return context.getArgument(arg, Text.class);
    }

    @Override
    public Text parse(StringReader reader) throws CommandSyntaxException {
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

        public Text parse() throws CommandSyntaxException {
            MutableText text = Text.empty();
            Style style = Style.EMPTY;
            while (reader.canRead()) {
                char c = reader.read();
                if (c == '&') { // Formatting.FORMATTING_CODE_PREFIX is not writable in chat
                    suggestor = suggestions -> {
                        SuggestionsBuilder builder = suggestions.createOffset(reader.getCursor());
                        CommandSource.suggestMatching(Arrays.stream(Formatting.values()).map(f -> String.valueOf(f.getCode())), builder);
                        suggestions.add(builder);
                    };
                    if (!reader.canRead()) {
                        throw EXPECTED_FORMATTING_CODE_EXCEPTION.create();
                    }
                    char code = reader.read();
                    Formatting formatting = Formatting.byCode(code);
                    if (formatting == null) {
                        throw UNKNOWN_FORMATTING_CODE_EXCEPTION.create();
                    }
                    style = style.withFormatting(formatting);
                } else if (c == '#') { // TextColor.RGB_PREFIX is private
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
                    text.append(Text.literal(String.valueOf(c)).setStyle(style));
                }
                suggestor = null;
            }
            return text;
        }
    }
}
