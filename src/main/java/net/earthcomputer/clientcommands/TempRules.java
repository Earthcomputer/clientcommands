package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.util.IStringSerializable;

public class TempRules {

	private static Map<String, Rule<?>> rules = new HashMap<>();

	public static final Rule<Boolean> ENCHANTING_PREDICTION = registerRule("enchantingPrediction", DataType.BOOLEAN,
			Boolean.FALSE).setHidden();
	public static final Rule<EnchantmentCracker.EnumCrackState> ENCHANTING_CRACK_STATE = registerRule(
			"enchantingCrackState", EnumDataType.of(EnchantmentCracker.EnumCrackState.class),
			EnchantmentCracker.EnumCrackState.UNCRACKED).setReadOnly().setHidden();
	public static final Rule<Double> BLOCK_REACH_DISTANCE = registerRule("blockReachDistance", DataType.DOUBLE.min(0),
			5.0);
	public static final Rule<Boolean> TOOL_BREAK_PROTECTION = registerRule("toolBreakProtection", DataType.BOOLEAN,
			Boolean.FALSE);
	public static final Rule<Boolean> MOCKING_TIME = registerRule("mockingTime", DataType.BOOLEAN, Boolean.FALSE)
			.setReadOnly();
	public static final Rule<Boolean> MOCKING_WEATHER = registerRule("mockingWeather", DataType.BOOLEAN, Boolean.FALSE)
			.setReadOnly();
	public static final Rule<Boolean> GHOST_BLOCK_FIX = registerRule("ghostBlockFix", DataType.BOOLEAN, Boolean.TRUE);

	public static boolean hasRule(String name) {
		return rules.containsKey(name);
	}

	public static Rule<?> getRule(String name) {
		return rules.get(name);
	}

	public static Collection<Rule<?>> getRules() {
		return rules.values();
	}

	public static List<String> getRuleNames() {
		return rules.keySet().stream().sorted().collect(Collectors.toList());
	}

	public static void resetToDefault() {
		rules.values().forEach(Rule::setToDefault);
	}

	public static <T> Rule<T> registerRule(String name, DataType<T> dataType, T defaultValue) {
		Rule<T> rule = new Rule<>(name, dataType, defaultValue);
		rules.put(name, rule);
		return rule;
	}

	public static class Rule<T> {
		private String name;
		private DataType<T> dataType;
		private T defaultValue;
		private T value;
		private boolean readOnly = false;
		private boolean hidden = false;
		private List<Consumer<ValueChangeEvent<T>>> listeners = new ArrayList<>();

		private Rule(String name, DataType<T> dataType, T defaultValue) {
			this.name = name;
			this.dataType = dataType;
			this.defaultValue = this.value = defaultValue;
		}

		public String getName() {
			return name;
		}

		public DataType<T> getDataType() {
			return dataType;
		}

		public T getDefaultValue() {
			return defaultValue;
		}

		public T getValue() {
			return value;
		}

		public void setValue(T value) {
			if (!value.equals(this.value)) {
				ValueChangeEvent<T> event = new ValueChangeEvent<>(this, this.value, value);
				listeners.forEach(l -> l.accept(event));
				this.value = value;
			}
		}

		public void setToDefault() {
			if (!value.equals(defaultValue)) {
				ValueChangeEvent<T> event = new ValueChangeEvent<>(this, value, defaultValue);
				listeners.forEach(l -> l.accept(event));
				value = defaultValue;
			}
		}

		public boolean isReadOnly() {
			return readOnly;
		}

		public Rule<T> setReadOnly() {
			readOnly = true;
			return this;
		}

		public boolean isHidden() {
			return hidden;
		}

		public Rule<T> setHidden() {
			hidden = true;
			return this;
		}

		public void addValueChangeListener(Consumer<ValueChangeEvent<T>> listener) {
			listeners.add(listener);
		}

		public void removeValueChangeListener(Consumer<ValueChangeEvent<T>> listener) {
			listeners.remove(listener);
		}
	}

	public static class ValueChangeEvent<T> {
		private Rule<T> rule;
		private T oldValue;
		private T newValue;

		public ValueChangeEvent(Rule<T> rule, T oldValue, T newValue) {
			this.rule = rule;
			this.oldValue = oldValue;
			this.newValue = newValue;
		}

		public Rule<T> getRule() {
			return rule;
		}

		public T getOldValue() {
			return oldValue;
		}

		public T getNewValue() {
			return newValue;
		}
	}

	public static interface DataType<T> {
		public static StringDataType STRING = new StringDataType();
		public static IntegerDataType INTEGER = new IntegerDataType();
		public static DoubleDataType DOUBLE = new DoubleDataType();
		public static DataType<Boolean> BOOLEAN = new DataType<Boolean>() {
			@Override
			public Boolean parse(String str) {
				return Boolean.parseBoolean(str);
			}

			@Override
			public List<String> getTabCompletionOptions() {
				return Arrays.asList("false", "true");
			}

			@Override
			public String toString(Boolean val) {
				return String.valueOf(val);
			}
		};

		T parse(String str) throws CommandException;

		List<String> getTabCompletionOptions();

		String toString(T val);
	}

	public static class StringDataType implements DataType<String> {
		private Predicate<String> filter = str -> true;

		public StringDataType filter(Predicate<String> filter) {
			StringDataType _new = new StringDataType();
			_new.filter = this.filter.and(filter);
			return _new;
		}

		@Override
		public String parse(String str) throws CommandException {
			if (!filter.test(str)) {
				throw new CommandException("tempRules.string.invalid", str);
			}
			return str;
		}

		@Override
		public List<String> getTabCompletionOptions() {
			return Collections.emptyList();
		}

		@Override
		public String toString(String val) {
			return val;
		}

	}

	public static class IntegerDataType implements DataType<Integer> {
		private int min = Integer.MIN_VALUE;
		private int max = Integer.MAX_VALUE;

		public IntegerDataType range(int min, int max) {
			IntegerDataType _new = new IntegerDataType();
			_new.min = min;
			_new.max = max;
			return _new;
		}

		public IntegerDataType min(int min) {
			return range(min, max);
		}

		public IntegerDataType max(int max) {
			return range(min, max);
		}

		@Override
		public Integer parse(String str) throws CommandException {
			return CommandBase.parseInt(str, min, max);
		}

		@Override
		public List<String> getTabCompletionOptions() {
			return Collections.emptyList();
		}

		@Override
		public String toString(Integer val) {
			return String.valueOf(val);
		}
	}

	public static class DoubleDataType implements DataType<Double> {
		private double min = -Double.MAX_VALUE;
		private double max = Double.MAX_VALUE;

		public DoubleDataType range(double min, double max) {
			DoubleDataType _new = new DoubleDataType();
			_new.min = min;
			_new.max = max;
			return _new;
		}

		public DoubleDataType min(double min) {
			return range(min, max);
		}

		public DoubleDataType max(double max) {
			return range(min, max);
		}

		@Override
		public Double parse(String str) throws CommandException {
			return CommandBase.parseDouble(str, min, max);
		}

		@Override
		public List<String> getTabCompletionOptions() {
			return Collections.emptyList();
		}

		@Override
		public String toString(Double val) {
			return String.valueOf(val);
		}
	}

	public static class EnumDataType<T extends Enum<T> & IStringSerializable> implements DataType<T> {
		private Map<String, T> nameToValue;

		private EnumDataType(Map<String, T> nameToValue) {
			this.nameToValue = nameToValue;
		}

		public static <T extends Enum<T> & IStringSerializable> EnumDataType<T> of(Class<T> clazz) {
			// https://stackoverflow.com/questions/33929304/weird-exception-invalid-receiver-type-class-java-lang-object-not-a-subtype-of
			// we can't use the method reference IStringSerializable::getName because of
			// this
			Map<String, T> nameToValue = Arrays.stream(clazz.getEnumConstants()).collect(
					Collectors.groupingBy(x -> x.getName(), Collectors.reducing(null, (a, b) -> a == null ? b : a)));
			return new EnumDataType<>(nameToValue);
		}

		@Override
		public T parse(String str) throws CommandException {
			T val = nameToValue.get(str);
			if (val == null) {
				throw new CommandException("tempRules.enum.invalid", str);
			}
			return val;
		}

		@Override
		public List<String> getTabCompletionOptions() {
			return nameToValue.keySet().stream().sorted().collect(Collectors.toList());
		}

		@Override
		public String toString(T val) {
			return val.getName();
		}
	}

}
