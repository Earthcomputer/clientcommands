package net.earthcomputer.clientcommands;

import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.minecraft.Util;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * @author Gaming32
 */
public final class UnsafeUtils {

    private UnsafeUtils() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Unsafe UNSAFE = Util.make(() -> {
        try {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (Exception e) {
            LOGGER.error("Could not access theUnsafe", e);
            return null;
        }
    });

    private static final MethodHandles.Lookup IMPL_LOOKUP = Util.make(() -> {
        try {
            //noinspection ConstantValue
            if (UNSAFE == null) {
                return null;
            }
            final Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            return (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), UNSAFE.staticFieldOffset(implLookupField));
        } catch (Exception e) {
            LOGGER.error("Could not access IMPL_LOOKUP", e);
            return null;
        }
    });

    public static Unsafe getUnsafe() {
        return UNSAFE;
    }

    public static MethodHandles.Lookup getImplLookup() {
        return IMPL_LOOKUP;
    }
}
