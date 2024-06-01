package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface PredicatedRangeArgument<T extends MinMaxBounds<?>> extends ArgumentType<T> {
    Dynamic2CommandExceptionType INTEGER_VALUE_TOO_LOW = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("argument.integer.low", a, b));
    Dynamic2CommandExceptionType INTEGER_VALUE_TOO_HIGH = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("argument.integer.low", a, b));
    Dynamic2CommandExceptionType FLOAT_VALUE_TOO_LOW = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("argument.float.low", a, b));
    Dynamic2CommandExceptionType FLOAT_VALUE_TOO_HIGH = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("argument.float.low", a, b));

    static Ints intRange(@Nullable Integer min, @Nullable Integer max) {
        return new Ints(min, max);
    }

    static Floats floatRange(@Nullable Double min, @Nullable Double max) {
        return new Floats(min, max);
    }
    
    class Ints implements PredicatedRangeArgument<MinMaxBounds.Ints> {
        @Nullable
        private final Integer min;
        @Nullable
        private final Integer max;

        public Ints(@Nullable Integer min, @Nullable Integer max) {
            this.min = min;
            this.max = max;
        }

        public static MinMaxBounds.Ints getRangeArgument(final CommandContext<FabricClientCommandSource> context, final String name) {
            return context.getArgument(name, MinMaxBounds.Ints.class);
        }

        @Override
        public MinMaxBounds.Ints parse(StringReader reader) throws CommandSyntaxException {
            MinMaxBounds.Ints range = MinMaxBounds.Ints.fromReader(reader);
            if (min != null && (range.min().isEmpty() || range.min().get() < min)) {
                throw INTEGER_VALUE_TOO_LOW.create(min, range.min().orElse(Integer.MIN_VALUE));
            }
            if (max != null && (range.max().isEmpty() || range.max().get() > max)) {
                throw INTEGER_VALUE_TOO_HIGH.create(max, range.max().orElse(Integer.MAX_VALUE));
            }
            return range;
        }
    }

    class Floats implements PredicatedRangeArgument<MinMaxBounds.Doubles> {
        @Nullable
        private final Double min;
        @Nullable
        private final Double max;

        public Floats(@Nullable Double min, @Nullable Double max) {
            this.min = min;
            this.max = max;
        }

        public static MinMaxBounds.Doubles getRangeArgument(final CommandContext<FabricClientCommandSource> context, final String name) {
            return context.getArgument(name, MinMaxBounds.Doubles.class);
        }

        @Override
        public MinMaxBounds.Doubles parse(StringReader reader) throws CommandSyntaxException {
            MinMaxBounds.Doubles range = MinMaxBounds.Doubles.fromReader(reader);
            if (min != null && (range.min().isEmpty() || range.min().get() < min)) {
                throw FLOAT_VALUE_TOO_LOW.create(min, range.min().orElse(-Double.MAX_VALUE));
            }
            if (max != null && (range.max().isEmpty() || range.max().get() < max)) {
                throw FLOAT_VALUE_TOO_HIGH.create(max, range.max().orElse(Double.MAX_VALUE));
            }
            return range;
        }
    }
}
