package net.earthcomputer.clientcommands;

import net.earthcomputer.clientcommands.features.*;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TempRules {

    @Rule(readOnly = true)
    public static double calcAnswer = 0;

    @Rule(readOnly = true)
    public static EnchantmentCracker.CrackState enchCrackState = EnchantmentCracker.CrackState.UNCRACKED;

    @Rule(readOnly = true)
    public static PlayerRandCracker.CrackState playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;

    @Rule(setter = "setEnchantingPrediction")
    private static boolean enchantingPrediction = false;
    public static boolean getEnchantingPrediction() {
        return enchantingPrediction;
    }
    public static void setEnchantingPrediction(boolean enchantingPrediction) {
        TempRules.enchantingPrediction = enchantingPrediction;
        if (enchantingPrediction)
            ServerBrandManager.rngWarning();
        else
            EnchantmentCracker.resetCracker();
    }

    public enum FishingManipulation implements StringIdentifiable {
        OFF,
        MANUAL,
        AFK;

        @Override
        public String asString() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public boolean isEnabled() {
            return this != OFF;
        }
    }

    @Rule(setter = "setFishingManipulation")
    private static FishingManipulation fishingManipulation = FishingManipulation.OFF;
    public static FishingManipulation getFishingManipulation() {
        return fishingManipulation;
    }
    public static void setFishingManipulation(FishingManipulation fishingManipulation) {
        TempRules.fishingManipulation = fishingManipulation;
        if (fishingManipulation.isEnabled()) {
            ServerBrandManager.rngWarning();
        } else {
            FishingCracker.reset();
        }
    }

    @Rule
    public static boolean playerRNGMaintenance = true;

    @Rule
    public static boolean toolBreakWarning = false;

    @Rule(setter = "setMaxEnchantItemThrows")
    public static int maxEnchantItemThrows = 64 * 32;
    public static void setMaxEnchantItemThrows(int maxEnchantItemThrows) {
        TempRules.maxEnchantItemThrows = MathHelper.clamp(maxEnchantItemThrows, 0, 1000000);
    }

    @Rule(setter = "setChorusManipulation")
    private static boolean chorusManipulation = false;
    public static boolean getChorusManipulation() {
        return chorusManipulation;
    }
    public static void setChorusManipulation(boolean chorusManipulation) {
        TempRules.chorusManipulation = chorusManipulation;
        if (chorusManipulation) {
            ServerBrandManager.rngWarning();
            ChorusManipulation.onChorusManipEnabled();
        }
    }
    @Rule(setter = "setSpeedManipulation")
    private static boolean speedManipulation = false;
    public static boolean getSpeedManipulation() {
        return speedManipulation;
    }
    public static void setSpeedManipulation(boolean speedManipulation) {
        TempRules.speedManipulation = speedManipulation;
        if (speedManipulation) {

            SpeedManipulation.onSpeedManipEnabled();
        }
    }
    @Rule(setter = "setMaxChorusItemThrows")
    public static int maxChorusItemThrows = 64 * 32;
    public static void setMaxChorusItemThrows(int maxChorusItemThrows) {
        TempRules.maxChorusItemThrows = MathHelper.clamp(maxChorusItemThrows, 0, 1000000);
    }

    @Rule
    public static boolean infiniteTools = false;

    public static String asString(Object value) {
        if (value instanceof StringIdentifiable) {
            return ((StringIdentifiable) value).asString();
        }
        return String.valueOf(value);
    }

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
        Consumer<Object> setter = setters.get(name);
        if (setter == null)
            throw new IllegalArgumentException();
        setter.accept(value);
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
    private static final Map<String, Consumer<Object>> setters = new HashMap<>();
    private static final Map<String, Object> defaults = new HashMap<>();
    static {
        for (Field field : TempRules.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Rule.class)) {
                rules.put(field.getName(), field);
                String setter = field.getAnnotation(Rule.class).setter();
                if (setter.isEmpty()) {
                    setters.put(field.getName(), val -> {
                        try {
                            field.set(null, val);
                        } catch (ReflectiveOperationException e) {
                            throw new AssertionError(e);
                        }
                    });
                } else {
                    Method setterMethod;
                    try {
                        setterMethod = TempRules.class.getMethod(setter, field.getType());
                    } catch (NoSuchMethodException e) {
                        throw new AssertionError(e);
                    }
                    setters.put(field.getName(), val -> {
                        try {
                            setterMethod.invoke(null, val);
                        } catch (ReflectiveOperationException e) {
                            throw new AssertionError(e);
                        }
                    });
                }
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
        String setter() default "";
    }

}
