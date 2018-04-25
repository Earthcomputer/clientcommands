package net.earthcomputer.clientcommands.command;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CommandCalc extends ClientCommandBase {

	private static final Map<String, DoubleSupplier> VARIABLES = new HashMap<>();
	private static final Map<String, ToDoubleFunction<double[]>> FUNCTIONS = new HashMap<>();

	static {
		VARIABLES.put("pi", () -> Math.PI);
		VARIABLES.put("e", () -> Math.E);
		VARIABLES.put("ans", TempRules.CALC_ANSWER::getValue);

		FUNCTIONS.put("sqrt", new SimpleFunction("sqrt", Math::sqrt));
		FUNCTIONS.put("abs", new SimpleFunction("abs", Math::abs));
		FUNCTIONS.put("ln", new SimpleFunction("ln", Math::log));
		FUNCTIONS.put("log", args -> {
			if (args.length == 1) {
				return Math.log10(args[0]);
			} else if (args.length == 2) {
				return Math.log(args[0]) / Math.log(args[1]);
			} else {
				throw new ArithmeticException("log takes 1 or 2 arguments");
			}
		});
		FUNCTIONS.put("sin", new SimpleFunction("sin", Math::sin));
		FUNCTIONS.put("cos", new SimpleFunction("cos", Math::cos));
		FUNCTIONS.put("tan", new SimpleFunction("tan", Math::tan));
		FUNCTIONS.put("csc", new SimpleFunction("csc", n -> 1 / Math.sin(n)));
		functionAlias("csc", "cosec");
		FUNCTIONS.put("sec", new SimpleFunction("sec", n -> 1 / Math.cos(n)));
		FUNCTIONS.put("cot", new SimpleFunction("cot", n -> 1 / Math.tan(n)));
		FUNCTIONS.put("asin", new SimpleFunction("asin", Math::asin));
		functionAlias("asin", "arsin");
		functionAlias("asin", "arcsin");
		FUNCTIONS.put("acos", new SimpleFunction("acos", Math::acos));
		functionAlias("acos", "arcos");
		functionAlias("acos", "arccos");
		FUNCTIONS.put("atan", new SimpleFunction("atan", Math::atan));
		functionAlias("atan", "artan");
		functionAlias("atan", "arctan");
		FUNCTIONS.put("atan2", args -> {
			if (args.length != 2) {
				throw new ArithmeticException("atan2 takes 2 arguments");
			} else {
				return Math.atan2(args[0], args[1]);
			}
		});
		FUNCTIONS.put("acsc", new SimpleFunction("acsc", n -> Math.asin(1 / n)));
		functionAlias("acsc", "arcsc");
		functionAlias("acsc", "arccsc");
		functionAlias("acsc", "acosec");
		functionAlias("acsc", "arcosec");
		functionAlias("acsc", "arccosec");
		FUNCTIONS.put("asec", new SimpleFunction("asec", n -> Math.acos(1 / n)));
		functionAlias("asec", "arsec");
		functionAlias("asec", "arcsec");
		FUNCTIONS.put("acot", new SimpleFunction("acot", n -> Math.atan(1 / n)));
		functionAlias("acot", "arcot");
		functionAlias("acot", "arccot");
		FUNCTIONS.put("sinh", new SimpleFunction("sinh", Math::sinh));
		FUNCTIONS.put("cosh", new SimpleFunction("cosh", Math::cosh));
		FUNCTIONS.put("tanh", new SimpleFunction("tanh", Math::tanh));
		FUNCTIONS.put("csch", new SimpleFunction("csch", n -> 1 / Math.sinh(n)));
		functionAlias("csch", "cosech");
		FUNCTIONS.put("sech", new SimpleFunction("sech", n -> 1 / Math.cosh(n)));
		FUNCTIONS.put("coth", new SimpleFunction("coth", n -> 1 / Math.tanh(n)));
		FUNCTIONS.put("asinh", new SimpleFunction("asinh", n -> Math.log(n + Math.sqrt(1 + n * n))));
		functionAlias("asinh", "arsinh");
		functionAlias("asinh", "arcsinh");
		FUNCTIONS.put("acosh", new SimpleFunction("acosh", n -> Math.log(n + Math.sqrt(n * n - 1))));
		functionAlias("acosh", "arcosh");
		functionAlias("acosh", "arccosh");
		FUNCTIONS.put("atanh", new SimpleFunction("atanh", n -> 0.5 * Math.log((1 + n) / (1 - n))));
		functionAlias("atanh", "artanh");
		functionAlias("atanh", "arctanh");
		FUNCTIONS.put("acsch", new SimpleFunction("acsch", n -> {
			if (n < 0) {
				return Math.log((1 - Math.sqrt(1 + n * n)) / n);
			} else if (n > 0) {
				return Math.log((1 + Math.sqrt(1 + n * n)) / n);
			} else {
				return Double.NaN;
			}
		}));
		functionAlias("acsch", "arcsch");
		functionAlias("acsch", "arccsch");
		functionAlias("acsch", "acosech");
		functionAlias("acsch", "arcosech");
		functionAlias("acsch", "arccosech");
		FUNCTIONS.put("asech", new SimpleFunction("asech", n -> {
			if (n < -1) {
				return Math.log((1 - Math.sqrt(1 - n * n)) / n);
			} else if (n > 0) {
				return Math.log((1 + Math.sqrt(1 - n * n)) / n);
			} else {
				return Double.NaN;
			}
		}));
		functionAlias("asech", "arsech");
		functionAlias("asech", "arcsech");
		FUNCTIONS.put("acoth", new SimpleFunction("acoth", n -> 0.5 * Math.log((n + 1) / (n - 1))));
		functionAlias("acoth", "arcoth");
		functionAlias("acoth", "arccoth");
	}

	private static void functionAlias(String old, String _new) {
		ToDoubleFunction<double[]> f = FUNCTIONS.get(old);
		if (f instanceof SimpleFunction) {
			f = new SimpleFunction(_new, ((SimpleFunction) f).f);
		}
		FUNCTIONS.put(_new, f);
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			throw new WrongUsageException(getUsage(sender));
		}

		// Parse the expression
		String expression = buildString(args, 0);
		Expression expr;
		try {
			StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(expression));
			tokenizer.parseNumbers();
			tokenizer.ordinaryChar('+');
			tokenizer.ordinaryChar('-');
			tokenizer.ordinaryChar('/');
			expr = Expression.parse(tokenizer);
			if (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
				throw syntaxError();
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		}

		// Evaluate the expression
		double ans;
		try {
			ans = expr.evaluate();
		} catch (ArithmeticException e) {
			throw new CommandException("commands.ccalc.mathError", e.getMessage());
		}

		TempRules.CALC_ANSWER.setValue(ans);

		// Output the formatted answer
		if (ans > 0 && ans % 1 == 0 && Double.isFinite(ans)) {
			// question = <b>answer</b> = itemstack_breakdown
			sender.sendMessage(new TextComponentString(
					String.format("%s = " + TextFormatting.BOLD + "%d" + TextFormatting.RESET + " = %d * 64 + %d",
							expression, (long) ans, (long) ans / 64, (long) ans % 64)));
		} else {
			// question = <b>answer</b>
			sender.sendMessage(new TextComponentString(
					expression + " = " + TextFormatting.BOLD + toString(ans) + TextFormatting.RESET));
		}
	}

	@Override
	public String getName() {
		return "ccalc";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.ccalc.usage";
	}

	private static CommandException syntaxError() {
		return new CommandException("commands.ccalc.syntaxError");
	}

	private static String toString(double val) {
		if (val % 1 == 0 && Double.isFinite(val)) {
			String str = String.valueOf(val);
			return str.substring(0, str.length() - 2);
		} else {
			return String.valueOf(val);
		}
	}

	/**
	 * An expression is a sum of products
	 */
	private static class Expression {
		private List<Sign> signs;
		private List<Product> products;

		private Expression(List<Sign> signs, List<Product> products) {
			this.signs = signs;
			this.products = products;
		}

		public static Expression parse(StreamTokenizer tokenizer) throws CommandException, IOException {
			List<Sign> signs = new ArrayList<>();
			List<Product> products = new ArrayList<>();

			// first product
			products.add(Product.parse(tokenizer));

			// the remaining products
			while (true) {
				Sign sign;
				switch (tokenizer.nextToken()) {
				case '+':
					sign = Sign.PLUS;
					break;
				case '-':
					sign = Sign.MINUS;
					break;
				default:
					// no more terms to parse
					tokenizer.pushBack();
					return new Expression(signs, products);
				}
				signs.add(sign);
				products.add(Product.parse(tokenizer));
			}
		}

		public double evaluate() {
			double val = products.get(0).evaluate();
			for (int i = 0; i < signs.size(); i++) {
				switch (signs.get(i)) {
				case PLUS:
					val += products.get(i + 1).evaluate();
					break;
				case MINUS:
					val -= products.get(i + 1).evaluate();
					break;
				}
			}
			return val;
		}
	}

	/**
	 * A product multiplies together simpler terms
	 */
	private static class Product {
		private List<MultSign> signs;
		private List<Term> terms;

		private Product(List<MultSign> signs, List<Term> terms) {
			this.signs = signs;
			this.terms = terms;
		}

		public static Product parse(StreamTokenizer tokenizer) throws CommandException, IOException {
			List<Term> terms = new ArrayList<>();
			List<MultSign> signs = new ArrayList<>();

			// first simpler term
			terms.add(Term.parse(tokenizer));

			// the remaining simpler terms
			while (true) {
				switch (tokenizer.nextToken()) {
				case '*':
					signs.add(MultSign.MULTIPLY);
					break;
				case '/':
					signs.add(MultSign.DIVIDE);
					break;
				case '(':
				case StreamTokenizer.TT_WORD:
					// implicit multiplication
					signs.add(MultSign.MULTIPLY);
					tokenizer.pushBack();
					break;
				case '^':
					// the previous simpler term is the base, parse the exponent
					terms.set(terms.size() - 1,
							new ExponentialTerm(terms.get(terms.size() - 1), Term.parse(tokenizer)));
					continue;
				default:
					// no more simpler terms to parse
					tokenizer.pushBack();
					return new Product(signs, terms);
				}
				terms.add(Term.parse(tokenizer));
			}
		}

		public double evaluate() {
			double val = terms.get(0).evaluate();
			for (int i = 0; i < signs.size(); i++) {
				switch (signs.get(i)) {
				case MULTIPLY:
					val *= terms.get(i + 1).evaluate();
					break;
				case DIVIDE:
					val /= terms.get(i + 1).evaluate();
					break;
				}
			}
			return val;
		}
	}

	/**
	 * A variable, constant, function, etc
	 */
	private static interface Term {
		public static Term parse(StreamTokenizer tokenizer) throws CommandException, IOException {
			// parse leading minus and plus signs
			boolean negative = false;

			do {
				if (tokenizer.nextToken() == '-') {
					negative = !negative;
				}
			} while (tokenizer.ttype == '+' || tokenizer.ttype == '-');
			tokenizer.pushBack();
			if (negative) {
				// if we need to negate, create a special term for that and re-parse without the
				// signs
				return new NegateTerm(parse(tokenizer));
			}

			switch (tokenizer.nextToken()) {
			case StreamTokenizer.TT_NUMBER:
				// if it's numerical, it's a number
				return new NumberTerm(tokenizer.nval);
			case StreamTokenizer.TT_WORD:
				// if it's a word, it's either a variable or a function
				DoubleSupplier supplier = VARIABLES.get(tokenizer.sval.toLowerCase(Locale.ENGLISH));
				if (supplier == null) {
					tokenizer.pushBack();
					return FunctionTerm.parse(tokenizer);
				} else {
					return new NumberTerm(supplier.getAsDouble());
				}
			case '(':
				// if it's an open parenthesis, it's a parenthesized nested expression
				tokenizer.pushBack();
				return ParenthesesTerm.parse(tokenizer);
			default:
				throw syntaxError();
			}
		}

		double evaluate();
	}

	/**
	 * Evaluates to a literal number
	 */
	private static class NumberTerm implements Term {
		private double number;

		public NumberTerm(double number) {
			this.number = number;
		}

		@Override
		public double evaluate() {
			return number;
		}
	}

	/**
	 * Evaluates the value of an expression, as a term
	 */
	private static class ParenthesesTerm implements Term {
		private Expression expr;

		private ParenthesesTerm(Expression expr) {
			this.expr = expr;
		}

		public static ParenthesesTerm parse(StreamTokenizer tokenizer) throws CommandException, IOException {
			if (tokenizer.nextToken() != '(') {
				throw syntaxError();
			}
			Expression expr = Expression.parse(tokenizer);
			if (tokenizer.nextToken() != ')') {
				throw syntaxError();
			}
			return new ParenthesesTerm(expr);
		}

		@Override
		public double evaluate() {
			return expr.evaluate();
		}
	}

	/**
	 * Evaluates its child, then negates the result
	 */
	private static class NegateTerm implements Term {
		private Term child;

		public NegateTerm(Term child) {
			this.child = child;
		}

		@Override
		public double evaluate() {
			return -child.evaluate();
		}
	}

	/**
	 * Raises one value to the power of another
	 */
	private static class ExponentialTerm implements Term {
		private Term base;
		private Term exponent;

		public ExponentialTerm(Term base, Term exponent) {
			this.base = base;
			this.exponent = exponent;
		}

		@Override
		public double evaluate() {
			return Math.pow(base.evaluate(), exponent.evaluate());
		}
	}

	/**
	 * Computes a function of other values
	 */
	private static class FunctionTerm implements Term {
		private ToDoubleFunction<double[]> f;
		private List<Expression> arguments;

		private FunctionTerm(ToDoubleFunction<double[]> f, List<Expression> arguments) {
			this.f = f;
			this.arguments = arguments;
		}

		public static FunctionTerm parse(StreamTokenizer tokenizer) throws CommandException, IOException {
			// function name
			if (tokenizer.nextToken() != StreamTokenizer.TT_WORD) {
				throw syntaxError();
			}

			// resolve the name
			ToDoubleFunction<double[]> f = FUNCTIONS.get(tokenizer.sval.toLowerCase(Locale.ENGLISH));
			if (f == null) {
				throw syntaxError();
			}

			// function arguments
			if (tokenizer.nextToken() != '(') {
				throw syntaxError();
			}
			List<Expression> args = new ArrayList<>();

			// first argument
			args.add(Expression.parse(tokenizer));
			// remaining arguments
			while (true) {
				switch (tokenizer.nextToken()) {
				case ',':
					args.add(Expression.parse(tokenizer));
					break;
				case ')':
					return new FunctionTerm(f, args);
				default:
					throw syntaxError();
				}
			}
		}

		@Override
		public double evaluate() {
			return f.applyAsDouble(arguments.stream().mapToDouble(Expression::evaluate).toArray());
		}
	}

	/**
	 * An additive sign +/-
	 */
	private static enum Sign {
		PLUS, MINUS
	}

	/**
	 * A multiplicative sign, * or /
	 */
	private static enum MultSign {
		MULTIPLY, DIVIDE
	}

	/**
	 * A helper class for functions which only take 1 argument
	 */
	private static class SimpleFunction implements ToDoubleFunction<double[]> {

		private String name;
		private DoubleUnaryOperator f;

		public SimpleFunction(String name, DoubleUnaryOperator f) {
			this.name = name;
			this.f = f;
		}

		@Override
		public double applyAsDouble(double[] args) {
			if (args.length != 1) {
				throw new ArithmeticException(name + " takes 1 argument");
			} else {
				return f.applyAsDouble(args[0]);
			}
		}

	}

}
