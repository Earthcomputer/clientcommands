package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;

public class ExpressionArgumentType implements ArgumentType<ExpressionArgumentType.Expression> {

    private static final Collection<String> EXAMPLES = Arrays.asList("123", "ans", "(1+2)", "1*3");

    private static final DynamicCommandExceptionType EXPECTED_EXCEPTION = new DynamicCommandExceptionType(obj -> new TranslatableText("commands.ccalc.expected", obj));
    private static final Dynamic2CommandExceptionType INVALID_ARGUMENT_COUNT = new Dynamic2CommandExceptionType((func, count) -> new TranslatableText("commands.ccalc.invalidArgumentCount", func, count));

    private ExpressionArgumentType() {}

    public static ExpressionArgumentType expression() {
        return new ExpressionArgumentType();
    }

    public static Expression getExpression(CommandContext<ServerCommandSource> context, String arg) {
        return context.getArgument(arg, Expression.class);
    }

    @Override
    public Expression parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        Expression ret = new Parser(reader).parseExpression();
        ret.strVal = reader.getString().substring(start, reader.getCursor());
        return ret;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());

        Parser parser = new Parser(reader);

        try {
            parser.parseExpression();
        } catch (CommandSyntaxException ignore) {
        }

        if (parser.suggestor != null)
            parser.suggestor.accept(builder);

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static class Parser {
        private StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        public Parser(StringReader reader) {
            this.reader = reader;
        }

        public Expression parseExpression() throws CommandSyntaxException {
            return parseExpression1();
        }

        private Expression parseExpression1() throws CommandSyntaxException {
            Expression left = parseExpression2();

            reader.skipWhitespace();
            if (reader.canRead()) {
                if (reader.peek() == '+') {
                    reader.skip();
                    reader.skipWhitespace();
                    Expression right = parseExpression1();
                    return new BinaryOpExpression(left, right, Double::sum);
                }

                if (reader.peek() == '-') {
                    reader.skip();
                    reader.skipWhitespace();
                    Expression right = parseExpression1();
                    return new BinaryOpExpression(left, right, (l, r) -> l - r);
                }
            }

            return left;
        }

        private Expression parseExpression2() throws CommandSyntaxException {
            Expression left = parseExpression3();

            reader.skipWhitespace();
            if (reader.canRead()) {
                if (reader.peek() == '*') {
                    reader.skip();
                    reader.skipWhitespace();
                    Expression right = parseExpression2();
                    return new BinaryOpExpression(left, right, (l, r) -> l * r);
                }

                if (reader.peek() == '/') {
                    reader.skip();
                    reader.skipWhitespace();
                    Expression right = parseExpression2();
                    return new BinaryOpExpression(left, right, (l, r) -> l / r);
                }

                if (reader.peek() == '%') {
                    reader.skip();
                    reader.skipWhitespace();
                    Expression right = parseExpression2();
                    return new BinaryOpExpression(left, right, (l, r) -> l % r);
                }

                if (!StringReader.isAllowedNumber(reader.peek())) {
                    int cursor = reader.getCursor();
                    try {
                        Expression right = parseExpression5();
                        return new BinaryOpExpression(left, right, (l, r) -> l * r);
                    } catch (CommandSyntaxException e) {
                        reader.setCursor(cursor);
                    }
                }
            }

            return left;
        }

        private Expression parseExpression3() throws CommandSyntaxException {
            List<Expression> subExpressions = new ArrayList<>();
            subExpressions.add(parseExpression4());

            reader.skipWhitespace();
            while (reader.canRead() && reader.peek() == '^') {
                reader.skip();
                reader.skipWhitespace();
                subExpressions.add(parseExpression4());
                reader.skipWhitespace();
            }

            if (subExpressions.size() == 1) {
                return subExpressions.get(0);
            } else {
                Expression right = subExpressions.get(subExpressions.size() - 1);
                for (int i = subExpressions.size() - 2; i >= 0; i--) {
                    Expression left = subExpressions.get(i);
                    right = new BinaryOpExpression(left, right, Math::pow);
                }
                return right;
            }
        }

        private Expression parseExpression4() throws CommandSyntaxException {
            boolean negative = false;
            while (reader.canRead() && (reader.peek() == '+' || reader.peek() == '-')) {
                if (reader.peek() == '-')
                    negative = !negative;
                reader.skip();
                reader.skipWhitespace();
            }

            Expression right = parseExpression5();
            if (negative)
                return new NegateExpression(right);
            else
                return right;
        }

        private Expression parseExpression5() throws CommandSyntaxException {
            int cursor = reader.getCursor();
            suggestor = builder -> {
                SuggestionsBuilder newBuilder = builder.createOffset(cursor);
                CommandSource.suggestMatching(ConstantExpression.CONSTANTS.keySet(), newBuilder);
                CommandSource.suggestMatching(FunctionExpression.FUNCTIONS.keySet(), newBuilder);
                builder.add(newBuilder);
            };

            String word = reader.readUnquotedString().toLowerCase(Locale.ENGLISH);

            if (ConstantExpression.CONSTANTS.containsKey(word)) {
                suggestor = null;
                return new ConstantExpression(ConstantExpression.CONSTANTS.get(word));
            }

            if (FunctionExpression.FUNCTIONS.containsKey(word)) {
                suggestor = null;
                reader.skipWhitespace();
                if (!reader.canRead() || reader.peek() != '(')
                    throw EXPECTED_EXCEPTION.createWithContext(reader, "(");
                reader.skip();
                reader.skipWhitespace();
                List<Expression> arguments = new ArrayList<>();
                if (reader.canRead() && reader.peek() != ')') {
                    arguments.add(parseExpression());
                    reader.skipWhitespace();
                    while (reader.canRead() && reader.peek() != ')') {
                        if (reader.peek() != ',')
                            throw EXPECTED_EXCEPTION.createWithContext(reader, ",");
                        reader.skip();
                        reader.skipWhitespace();
                        arguments.add(parseExpression());
                        reader.skipWhitespace();
                    }
                }
                if (!reader.canRead())
                    throw EXPECTED_EXCEPTION.createWithContext(reader, ")");
                reader.skip();

                FunctionExpression.IFunction function = FunctionExpression.FUNCTIONS.get(word);
                if (!function.isAcceptableInputCount(arguments.size())) {
                    reader.setCursor(cursor);
                    reader.readUnquotedString();
                    throw INVALID_ARGUMENT_COUNT.createWithContext(reader, word, arguments.size());
                }

                return new FunctionExpression(function, arguments.toArray(new Expression[0]));
            }

            reader.setCursor(cursor);

            if (reader.canRead() && reader.peek() == '(')
                suggestor = null;

            Expression ret = parseExpression6();
            suggestor = null;
            return ret;
        }

        private Expression parseExpression6() throws CommandSyntaxException {
            if (reader.canRead() && reader.peek() == '(') {
                reader.skip();
                reader.skipWhitespace();
                Expression ret = parseExpression();
                reader.skipWhitespace();
                if (!reader.canRead() || reader.peek() != ')')
                    throw EXPECTED_EXCEPTION.createWithContext(reader, ")");
                reader.skip();
                return ret;
            }
            return parseExpression7();
        }

        private Expression parseExpression7() throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && isAllowedNumber(reader.peek()))
                reader.skip();
            String number = reader.getString().substring(start, reader.getCursor());
            if (number.isEmpty())
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedDouble().createWithContext(reader);
            try {
                return new LiteralExpression(Double.parseDouble(number));
            } catch (NumberFormatException e) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, number);
            }
        }

        private static boolean isAllowedNumber(char c) {
            return (c >= '0' && c <= '9') || c == '.';
        }
    }

    public static abstract class Expression {
        public String strVal;
        public abstract double eval();
    }

    private static class BinaryOpExpression extends Expression {

        private Expression left;
        private Expression right;
        private DoubleBinaryOperator operator;

        public BinaryOpExpression(Expression left, Expression right, DoubleBinaryOperator operator) {
            this.left = left;
            this.right = right;
            this.operator = operator;
        }

        @Override
        public double eval() {
            return operator.applyAsDouble(left.eval(), right.eval());
        }
    }

    private static class NegateExpression extends Expression {
        private Expression right;

        public NegateExpression(Expression right) {
            this.right = right;
        }

        @Override
        public double eval() {
            return -right.eval();
        }
    }

    private static class ConstantExpression extends Expression {

        private static final Map<String, DoubleSupplier> CONSTANTS = ImmutableMap.of(
                "pi", () -> Math.PI,
                "e", () -> Math.E,
                "ans", () -> TempRules.calcAnswer
        );

        private DoubleSupplier constant;

        public ConstantExpression(DoubleSupplier constant) {
            this.constant = constant;
        }

        @Override
        public double eval() {
            return constant.getAsDouble();
        }
    }

    public static class FunctionExpression extends Expression {

        private static Map<String, IFunction> FUNCTIONS = ImmutableMap.<String, IFunction>builder()
                .put("sqrt", (UnaryFunction) Math::sqrt)
                .put("abs", (UnaryFunction) Math::abs)
                .put("ln", (UnaryFunction) Math::log)
                .put("log", new IFunction() {
                    @Override
                    public double eval(double... inputs) {
                        if (inputs.length == 1) {
                            return Math.log10(inputs[0]);
                        } else {
                            return Math.log(inputs[0]) / Math.log(inputs[1]);
                        }
                    }

                    @Override
                    public boolean isAcceptableInputCount(int count) {
                        return count == 1 || count == 2;
                    }
                })
                .put("sin", (UnaryFunction) Math::sin)
                .put("cos", (UnaryFunction) Math::cos)
                .put("tan", (UnaryFunction) Math::tan)
                .put("csc", (UnaryFunction) n -> 1 / Math.sin(n))
                .put("sec", (UnaryFunction) n -> 1 / Math.cos(n))
                .put("cot", (UnaryFunction) n -> 1 / Math.tan(n))
                .put("asin", (UnaryFunction) Math::asin)
                .put("acos", (UnaryFunction) Math::acos)
                .put("atan", (UnaryFunction) Math::atan)
                .put("atan2", (BinaryFunction) Math::atan2)
                .put("acsc", (UnaryFunction) n -> Math.asin(1 / n))
                .put("asec", (UnaryFunction) n -> Math.acos(1 / n))
                .put("acot", (UnaryFunction) n -> Math.atan(1 / n))
                .put("sinh", (UnaryFunction) Math::sinh)
                .put("cosh", (UnaryFunction) Math::cosh)
                .put("tanh", (UnaryFunction) Math::tanh)
                .put("csch", (UnaryFunction) n -> 1 / Math.sinh(n))
                .put("sech", (UnaryFunction) n -> 1 / Math.cosh(n))
                .put("coth", (UnaryFunction) n -> 1 / Math.tanh(n))
                .put("asinh", (UnaryFunction) n -> Math.log(n + Math.sqrt(1 + n * n)))
                .put("acosh", (UnaryFunction) n -> Math.log(n + Math.sqrt(n * n - 1)))
                .put("atanh", (UnaryFunction) n -> 0.5 * Math.log((1 + n) / (1 - n)))
                .put("acsch", (UnaryFunction) n -> {
                    if (n < 0) {
                        return Math.log((1 - Math.sqrt(1 + n * n)) / n);
                    } else if (n > 0) {
                        return Math.log((1 + Math.sqrt(1 + n * n)) / n);
                    } else {
                        return Double.NaN;
                    }
                })
                .put("asech", (UnaryFunction) n -> {
                    if (n < -1) {
                        return Math.log((1 - Math.sqrt(1 - n * n)) / n);
                    } else if (n > 0) {
                        return Math.log((1 + Math.sqrt(1 - n * n)) / n);
                    } else {
                        return Double.NaN;
                    }
                })
                .put("acoth", (UnaryFunction) n -> 0.5 * Math.log((n + 1) / (n - 1)))
                .put("and", (TwoOrMoreFunction) vals -> (double)Arrays.stream(vals).mapToInt(val -> (int) val).reduce(0, (a, b) -> a & b))
                .put("or", (TwoOrMoreFunction) vals -> (double)Arrays.stream(vals).mapToInt(val -> (int) val).reduce(0, (a, b) -> a | b))
                .put("xor", (TwoOrMoreFunction) vals -> (double)Arrays.stream(vals).mapToInt(val -> (int) val).reduce(0, (a, b) -> a ^ b))
                .put("not", (UnaryFunction) val -> (double)(~((int)val)))
        .build();

        private IFunction function;
        private Expression[] arguments;

        public FunctionExpression(IFunction function, Expression... arguments) {
            this.function = function;
            this.arguments = arguments;
        }

        @Override
        public double eval() {
            double[] args = new double[arguments.length];
            for (int i = 0; i < args.length; i++)
                args[i] = arguments[i].eval();
            return function.eval(args);
        }

        private static interface IFunction {
            double eval(double... inputs);
            boolean isAcceptableInputCount(int count);
        }

        @FunctionalInterface
        private static interface UnaryFunction extends IFunction {
            double evalUnary(double n);

            @Override
            default double eval(double... inputs) {
                return evalUnary(inputs[0]);
            }

            @Override
            default boolean isAcceptableInputCount(int count) {
                return count == 1;
            }
        }

        @FunctionalInterface
        private static interface BinaryFunction extends IFunction {
            double evalBinary(double a, double b);

            @Override
            default double eval(double... inputs) {
                return evalBinary(inputs[0], inputs[1]);
            }

            @Override
            default boolean isAcceptableInputCount(int count) {
                return count == 2;
            }
        }

        @FunctionalInterface
        private static interface TwoOrMoreFunction extends IFunction {
            @Override
            default boolean isAcceptableInputCount(int count) {
                return count >= 2;
            }
        }

    }

    private static class LiteralExpression extends Expression {
        private double val;

        public LiteralExpression(double val) {
            this.val = val;
        }

        @Override
        public double eval() {
            return val;
        }
    }

}
