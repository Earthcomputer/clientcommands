package net.earthcomputer.clientcommands.server;

import dev.xpple.betterconfig.api.Config;

public class ServerConfigs {
    public static final String MOD_ID = "clientcommands-server";

    @Config(onChange = "onChangePermission")
    public static boolean serverFindItem = true;

    private static void onChangePermission(boolean oldValue, boolean newValue) {
        ClientCommandsServer.onPermissionsChanged();
    }
}
