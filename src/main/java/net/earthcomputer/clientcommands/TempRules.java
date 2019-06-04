package net.earthcomputer.clientcommands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class TempRules {

    @Rule(readOnly = true)
    public static double calcAnswer = 0;

    public static Object get(String name) {
        Field field = rules.get(name);
        if (field == null)
            throw new IllegalArgumentException();
        try {
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static void set(String name, Object value) {
        Field field = rules.get(name);
        if (field == null)
            throw new IllegalArgumentException();
        try {
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static void reset(String name) {
        set(name, defaults.get(name));
    }

    public static Class<?> getType(String name) {
        Field field = rules.get(name);
        if (field == null)
            throw new IllegalArgumentException();
        return field.getType();
    }

    public static List<String> getRules() {
        return new ArrayList<>(rules.keySet());
    }

    public static List<String> getWritableRules() {
        return rules.keySet().stream().filter(rule -> !rules.get(rule).getAnnotation(Rule.class).readOnly()).collect(Collectors.toCollection(ArrayList::new));
    }

    private static final Map<String, Field> rules = new HashMap<>();
    private static final Map<String, Object> defaults = new HashMap<>();
    static {
        for (Field field : TempRules.class.getFields()) {
            if (field.isAnnotationPresent(Rule.class)) {
                rules.put(field.getName(), field);
                try {
                    defaults.put(field.getName(), field.get(null));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    private static @interface Rule {
        boolean readOnly() default false;
    }

}
