package net.earthcomputer.clientcommands.server;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

public enum ServerPermission {
    FIND_ITEM(0, () -> ServerConfigs.serverFindItem),
    ;

    private final int flag;
    private final BooleanSupplier allowedByConfig;

    ServerPermission(int bit, BooleanSupplier allowedByConfig) {
        this.flag = 1 << bit;
        this.allowedByConfig = allowedByConfig;
    }

    // Only meaningful on the server
    public boolean isAllowed() {
        return allowedByConfig.getAsBoolean();
    }

    public static int getAllowedFlags() {
        int flags = 0;
        for (ServerPermission permission : values()) {
            if (permission.isAllowed()) {
                flags |= permission.flag;
            }
        }
        return flags;
    }

    public static Set<ServerPermission> fromFlags(int flags) {
        Set<ServerPermission> permissions = EnumSet.noneOf(ServerPermission.class);
        for (ServerPermission permission : values()) {
            if ((flags & permission.flag) != 0) {
                permissions.add(permission);
            }
        }
        return permissions;
    }
}
