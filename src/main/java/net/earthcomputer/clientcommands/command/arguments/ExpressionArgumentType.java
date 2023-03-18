package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.mixin.ChatInputSuggestorAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;

public class ExpressionArgumentType implements ArgumentType<ExpressionArgumentType.Expression> {

    private static final Collection<String> EXAMPLES = Arrays.asList("123", "ans", "(1+2)", "1*3");

    private static final DynamicCommandExceptionType EXPECTED_EXCEPTION = new DynamicCommandExceptionType(obj -> Text.translatable("commands.ccalc.expected", obj));
    private static final Dynamic2CommandExceptionType INVALID_ARGUMENT_COUNT = new Dynamic2CommandExceptionType((func, count) -> Text.translatable("commands.ccalc.invalidArgumentCount", func, count));
    private static final SimpleCommandExceptionType TOO_DEEPLY_NESTED = new SimpleCommandExceptionType(Text.translatable("commands.ccalc.tooDeeplyNested"));

    private ExpressionArgumentType() {}

    public static ExpressionArgumentType expression() {
        return new ExpressionArgumentType();
    }

    public static Expression getExpression(CommandContext<FabricClientCommandSource> context, String arg) {
        return context.getArgument(arg, Expression.class);
    }

    @Override
    public Expression parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        Expression ret = new Parser(reader).parse();
        ret.strVal = reader.getString().substring(start, reader.getCursor());
        return ret;
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

        public Expression parse() throws CommandSyntaxException {
            try {
                return parseExpression();
            } catch (StackOverflowError e) {
                suggestor = null;
                throw TOO_DEEPLY_NESTED.create();
            }
        }

        // <Expression> ::= <Term> | (<Expression> ("+" | "-") <Term>)
        private Expression parseExpression() throws CommandSyntaxException {
            Expression expr = parseTerm();
            reader.skipWhitespace();

            while (reader.canRead() && (reader.peek() == '+' || reader.peek() == '-')) {
                char operator = reader.read();
                reader.skipWhitespace();
                Expression right = parseTerm();
                if (operator == '+') {
                    expr = new BinaryOpExpression(expr, right, Double::sum, "addition");
                } else {
                    expr = new BinaryOpExpression(expr, right, (l, r) -> l - r, "subtraction");
                }
            }

            return expr;
        }

        // <Term> ::= <Unary> | (<Term> ("*" | "/" | "%") <Unary>)
        private Expression parseTerm() throws CommandSyntaxException {
            Expression expr = parseUnary();
            reader.skipWhitespace();

            while (reader.canRead() && (reader.peek() == '*' || reader.peek() == '/' || reader.peek() == '%')) {
                char operator = reader.read();
                reader.skipWhitespace();
                Expression right = parseUnary();
                if (operator == '*') {
                    expr = new BinaryOpExpression(expr, right, (l, r) -> l * r, "multiplication");
                } else if (operator == '/') {
                    expr = new BinaryOpExpression(expr, right, (l, r) -> l / r, "division");
                } else {
                    expr = new BinaryOpExpression(expr, right, (l, r) -> l % r, "modulo");
                }
            }

            return expr;
        }

        // <Unary> ::= ("+" | "-")* <ImplicitMult>
        private Expression parseUnary() throws CommandSyntaxException {
            boolean negative = false;
            while (reader.canRead() && (reader.peek() == '+' || reader.peek() == '-')) {
                if (reader.peek() == '-') {
                    negative = !negative;
                }
                reader.skip();
                reader.skipWhitespace();
            }

            Expression right = parseImplicitMult();
            if (negative) {
                return new NegateExpression(right);
            } else {
                return right;
            }
        }

        // <ImplicitMult> ::= <Exponentiation> | (<not lookahead Literal> <ImplicitMult>)
        private Expression parseImplicitMult() throws CommandSyntaxException {
            Expression expr = parseExponentiation();

            if (reader.canRead() && !StringReader.isAllowedNumber(reader.peek())) {
                int cursor = reader.getCursor();
                try {
                    Expression right = parseImplicitMult();
                    return new BinaryOpExpression(expr, right, (l, r) -> l * r, "multiplication");
                } catch (CommandSyntaxException e) {
                    reader.setCursor(cursor);
                }
            }

            return expr;
        }

        // <Exponentiation> ::= <ConstantOrFunction> | (<ConstantOrFunction> "^" <Unary>)
        private Expression parseExponentiation() throws CommandSyntaxException {
            Expression expr = parseConstantOrFunction();
            reader.skipWhitespace();
            if (reader.canRead() && reader.peek() == '^') {
                reader.skip();
                reader.skipWhitespace();
                Expression exponent = parseUnary();
                return new BinaryOpExpression(expr, exponent, Math::pow, "exponentiation");
            }

            return expr;
        }

        // <ConstantOrFunction> ::= <Parenthesized> | <Constant> | <Function>
        // <Constant> ::= any of the defined constants
        // <Function> ::= <FunctionName> "(" <FunctionArgumentList> ")"
        // <FunctionName> ::= any of the defined function names
        // <FunctionArgumentList> ::= <Expression> | (<Expression> "," <FunctionArgumentList>)
        private Expression parseConstantOrFunction() throws CommandSyntaxException {
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
                return new ConstantExpression(word, ConstantExpression.CONSTANTS.get(word));
            }

            if (FunctionExpression.FUNCTIONS.containsKey(word)) {
                suggestor = null;
                reader.skipWhitespace();
                if (!reader.canRead() || reader.peek() != '(') {
                    throw EXPECTED_EXCEPTION.createWithContext(reader, "(");
                }
                reader.skip();
                reader.skipWhitespace();
                List<Expression> arguments = new ArrayList<>();
                if (reader.canRead() && reader.peek() != ')') {
                    arguments.add(parseExpression());
                    reader.skipWhitespace();
                    while (reader.canRead() && reader.peek() != ')') {
                        if (reader.peek() != ',') {
                            throw EXPECTED_EXCEPTION.createWithContext(reader, ",");
                        }
                        reader.skip();
                        reader.skipWhitespace();
                        arguments.add(parseExpression());
                        reader.skipWhitespace();
                    }
                }
                if (!reader.canRead()) {
                    throw EXPECTED_EXCEPTION.createWithContext(reader, ")");
                }
                reader.skip();

                var function = FunctionExpression.FUNCTIONS.get(word);
                if (!function.isAcceptableInputCount(arguments.size())) {
                    reader.setCursor(cursor);
                    reader.readUnquotedString();
                    throw INVALID_ARGUMENT_COUNT.createWithContext(reader, word, arguments.size());
                }

                return new FunctionExpression(word, function, arguments.toArray(new Expression[0]));
            }

            reader.setCursor(cursor);

            if (reader.canRead() && reader.peek() == '(')
                suggestor = null;

            Expression ret = parseParenthesized();
            suggestor = null;
            return ret;
        }

        // <Parenthesized> ::= <Literal> | ("(" <Expression> ")")
        private Expression parseParenthesized() throws CommandSyntaxException {
            if (reader.canRead() && reader.peek() == '(') {
                reader.skip();
                reader.skipWhitespace();
                Expression ret = parseExpression();
                reader.skipWhitespace();
                if (!reader.canRead() || reader.peek() != ')') {
                    throw EXPECTED_EXCEPTION.createWithContext(reader, ")");
                }
                reader.skip();
                return ret;
            }
            return parseLiteral();
        }

        // <Literal> ::= ("0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | ".")+
        private Expression parseLiteral() throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && isAllowedNumber(reader.peek())) {
                reader.skip();
            }
            String number = reader.getString().substring(start, reader.getCursor());
            if (number.isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedDouble().createWithContext(reader);
            }
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
        public abstract double eval() throws StackOverflowError;
        public abstract Text getParsedTree(int depth) throws StackOverflowError;

        protected static Text getDepthStyled(int depth, MutableText text) {
            List<Style> formattings = ChatInputSuggestorAccessor.getHighlightStyles();
            return text.setStyle(formattings.get(depth % formattings.size()));
        }
    }

    private static class BinaryOpExpression extends Expression {
        private final Expression left;
        private final Expression right;
        private final DoubleBinaryOperator operator;
        private final String type;

        public BinaryOpExpression(Expression left, Expression right, DoubleBinaryOperator operator, String type) {
            this.left = left;
            this.right = right;
            this.operator = operator;
            this.type = type;
        }

        @Override
        public double eval() {
            return operator.applyAsDouble(left.eval(), right.eval());
        }

        @Override
        public Text getParsedTree(int depth) {
            return getDepthStyled(depth, Text.translatable("commands.ccalc.parse.binaryOperator." + type, left.getParsedTree(depth + 1), right.getParsedTree(depth + 1)));
        }
    }

    private static class NegateExpression extends Expression {
        private final Expression right;

        public NegateExpression(Expression right) {
            this.right = right;
        }

        @Override
        public double eval() {
            return -right.eval();
        }

        @Override
        public Text getParsedTree(int depth) {
            return getDepthStyled(depth, Text.translatable("commands.ccalc.parse.negate", this.right.getParsedTree(depth + 1)));
        }
    }

    private static class ConstantExpression extends Expression {

        private static final Map<String, DoubleSupplier> CONSTANTS = ImmutableMap.of(
                "pi", () -> Math.PI,
                "e", () -> Math.E,
                "ans", () -> TempRules.calcAnswer
        );

        private final String type;
        private final DoubleSupplier constant;

        public ConstantExpression(String type, DoubleSupplier constant) {
            this.type = type;
            this.constant = constant;
        }

        @Override
        public double eval() {
            return constant.getAsDouble();
        }

        @Override
        public Text getParsedTree(int depth) {
            return getDepthStyled(depth, Text.translatable("commands.ccalc.parse.constant", this.type));
        }
    }

    public static class FunctionExpression extends Expression {

        private static final Map<String, IFunction> FUNCTIONS = ImmutableMap.<String, IFunction>builder()
                .put("sqrt", (UnaryFunction) Math::sqrt)
                .put("abs", (UnaryFunction) Math::abs)
                .put("floor", (UnaryFunction) Math::floor)
                .put("ceil", (UnaryFunction) Math::ceil)
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

        private final String type;
        private final IFunction function;
        private final Expression[] arguments;

        public FunctionExpression(String type, IFunction function, Expression... arguments) {
            this.type = type;
            this.function = function;
            this.arguments = arguments;
        }

        @Override
        public double eval() {
            double[] args = new double[arguments.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = arguments[i].eval();
            }
            return function.eval(args);
        }

        @Override
        public Text getParsedTree(int depth) {
            MutableText argumentsText = Text.literal("");
            boolean first = true;
            for (Expression argument : this.arguments) {
                if (first) {
                    first = false;
                } else {
                    argumentsText.append(", ");
                }

                argumentsText.append(argument.getParsedTree(depth + 1));
            }

            return getDepthStyled(depth, Text.translatable("commands.ccalc.parse.function", this.type, argumentsText));
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
        private final double val;

        public LiteralExpression(double val) {
            this.val = val;
        }

        @Override
        public double eval() {
            return val;
        }

        @Override
        public Text getParsedTree(int depth) {
            return getDepthStyled(depth, Text.translatable("commands.ccalc.parse.literal", this.val));
        }
    }

}
