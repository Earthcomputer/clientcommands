package net.earthcomputer.clientcommands.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RunnableClickEventActionHelper {

    public static final Map<String, Runnable> runnables = new HashMap<>();

    public static String registerCode(Runnable code) {
        String randomString = new Random().ints(48, 122 + 1) // 0 to z
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(10)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        runnables.put(randomString, code);
        return randomString;
    }
}
