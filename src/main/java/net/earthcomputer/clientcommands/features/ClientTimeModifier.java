package net.earthcomputer.clientcommands.features;

import java.util.function.Function;

public class ClientTimeModifier {

    public enum Type {
        NONE(time -> time),
        OFFSET(time -> time + value),
        LOCK(time -> value);

        private final Function<Long, Long> timeMappingFunction;

        Type(Function<Long, Long> timeMappingFunction) {
            this.timeMappingFunction = timeMappingFunction;
        }

        public long getModifiedTime(long timeOfDay) {
            return timeMappingFunction.apply(timeOfDay);
        }
    }

    private static Type type = Type.NONE;
    private static long value = 0;

    public static void none() {
        type = Type.NONE;
    }

    public static void offset(long time) {
        type = Type.OFFSET;
        value = time;
    }

    public static void lock(long time) {
        type = Type.LOCK;
        value = time;
    }

    public static long getModifiedTime(long timeOfDay) {
        return type.getModifiedTime(timeOfDay);
    }
}
