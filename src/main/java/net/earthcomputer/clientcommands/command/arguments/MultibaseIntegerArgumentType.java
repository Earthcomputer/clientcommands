package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Arrays;
import java.util.Collection;

public class MultibaseIntegerArgumentType implements ArgumentType<Integer> {

    private static final Collection<String> EXAMPLES = Arrays.asList("0", "123", "-123", "0xf00baa", "0xF00BAA", "0123", "0b101");

    private final int minimum;
    private final int maximum;

    private MultibaseIntegerArgumentType(final int minimum, final int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static MultibaseIntegerArgumentType multibaseInteger() {
        return multibaseInteger(Integer.MIN_VALUE);
    }

    public static MultibaseIntegerArgumentType multibaseInteger(final int min) {
        return multibaseInteger(min, Integer.MAX_VALUE);
    }

    public static MultibaseIntegerArgumentType multibaseInteger(final int min, final int max) {
        return new MultibaseIntegerArgumentType(min, max);
    }

    public static int getMultibaseInteger(final CommandContext<?> context, final String name) {
        return context.getArgument(name, int.class);
    }

    public int getMinimum() {
        return minimum;
    }

    public int getMaximum() {
        return maximum;
    }

    @Override
    public Integer parse(final StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        final int result;
        if (reader.peek() == '0') {
            reader.skip();
            if (!reader.canRead()) {
                result = 0;
            } else if (reader.peek() == 'x' || reader.peek() == 'X') {
                reader.skip();
                int nStart = reader.getCursor();
                while (reader.canRead() && ((reader.peek() >= '0' && reader.peek() <= '9') || (reader.peek() >= 'a' && reader.peek() <= 'f') || (reader.peek() >= 'A' && reader.peek() <= 'F')))
                    reader.skip();
                try {
                    result = Integer.parseInt(reader.getString().substring(nStart, reader.getCursor()), 16);
                } catch (NumberFormatException e) {
                    reader.setCursor(start);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
                }
            } else if (reader.peek() == 'b' || reader.peek() == 'B') {
                reader.skip();
                int nStart = reader.getCursor();
                while (reader.canRead() && (reader.peek() == '0' || reader.peek() == '1'))
                    reader.skip();
                try {
                    result = Integer.parseInt(reader.getString().substring(nStart, reader.getCursor()), 2);
                } catch (NumberFormatException e) {
                    reader.setCursor(start);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
                }
            } else if (reader.peek() >= '0' && reader.peek() <= '7') {
                int nStart = reader.getCursor();
                while (reader.canRead() && reader.peek() >= '0' && reader.peek() <= '7')
                    reader.skip();
                try {
                    result = Integer.parseInt(reader.getString().substring(nStart, reader.getCursor()), 8);
                } catch (NumberFormatException e) {
                    reader.setCursor(start);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
                }
            } else {
                reader.setCursor(start);
                result = reader.readInt();
            }
        } else {
            result = reader.readInt();
        }
        if (result < minimum) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, result, minimum);
        }
        if (result > maximum) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().createWithContext(reader, result, maximum);
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final MultibaseIntegerArgumentType that)) return false;

        return maximum == that.maximum && minimum == that.minimum;
    }

    @Override
    public int hashCode() {
        return 31 * minimum + maximum;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
