package net.earthcomputer.clientcommands.c2c;

import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class OutgoingPacketFilter {

    private static final Set<String> cache = Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).<String, Boolean>build().asMap());

    public static boolean removeIfContains(String packetString) {
        return cache.remove(packetString);
    }

    public static void addPacket(String packetString) {
        cache.add(packetString);
    }

    public static void clear() {
        cache.clear();
    }
}
