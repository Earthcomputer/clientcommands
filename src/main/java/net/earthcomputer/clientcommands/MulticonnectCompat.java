package net.earthcomputer.clientcommands;

import net.minecraft.SharedConstants;
import net.minecraft.registry.Registry;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class MulticonnectCompat {
    public static final int V1_13_2 = 404;
    public static final int V1_14 = 477;
    public static final int V1_14_2 = 485;
    public static final int V1_15 = 573;
    public static final int V1_15_2 = 578;
    public static final int V1_17 = 755;
    public static final int V1_18 = 757;

    private MulticonnectCompat() {
    }

    @Nullable
    private static final Object api = Util.make(() -> {
        Class<?> clazz;
        try {
            clazz = Class.forName("net.earthcomputer.multiconnect.api.MultiConnectAPI");
        } catch (ClassNotFoundException e) {
            return null;
        }
        try {
            return clazz.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    });
    private static final Method getProtocolVersion = Util.make(() -> {
        if (api == null) {
            return null;
        }
        try {
            return api.getClass().getMethod("getProtocolVersion");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    });
    private static final Method doesServerKnow = Util.make(() -> {
        if (api == null) {
            return null;
        }
        try {
            return api.getClass().getMethod("doesServerKnow", Registry.class, Object.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    });

    public static int getProtocolVersion() {
        if (api == null) {
            return SharedConstants.getProtocolVersion();
        } else {
            try {
                return (Integer) getProtocolVersion.invoke(api);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static <T> boolean doesServerKnow(Registry<T> registry, T value) {
        if (api == null) {
            return true;
        } else {
            try {
                return (Boolean) doesServerKnow.invoke(api, registry, value);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
