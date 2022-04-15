package net.earthcomputer.clientcommands.features;

public class ClientTimeModifier {

    public static enum Type {
        NONE, OFFSET, LOCK;
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
        return switch (type) {
            case NONE -> timeOfDay;
            case LOCK -> value;
            case OFFSET -> timeOfDay < 0 ? timeOfDay - value : timeOfDay + value;
            default -> throw new IllegalStateException("Not implemented for type " + type);
        };
    }
}
