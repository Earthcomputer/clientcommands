package net.earthcomputer.clientcommands.command;

import org.jetbrains.annotations.Nullable;

public final class Argument<T> {
    private final String name;
    private final T defaultValue;

    private Argument(String name, @Nullable T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public static <T> Argument<@Nullable T> of(String name) {
        return new Argument<>(name, null);
    }

    public static <T> Argument<T> ofDefaulted(String name, T defaultValue) {
        return new Argument<>(name, defaultValue);
    }

    public static Argument<Boolean> ofFlag(String name) {
        return new Argument<>(name, false);
    }

    public String getName() {
        return name;
    }

    public String getFlag() {
        return "--" + name;
    }

    public static boolean isFlag(String argument) {
        return argument.startsWith("--");
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}
