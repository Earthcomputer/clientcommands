package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.server.PermissionsPayload;
import net.earthcomputer.clientcommands.server.ServerPermission;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.EnumSet;
import java.util.Set;

public final class ServerPermissions {
    private static Set<ServerPermission> permissions = EnumSet.noneOf(ServerPermission.class);

    private ServerPermissions() {
    }

    public static void registerEvents() {
        ClientPlayNetworking.registerGlobalReceiver(PermissionsPayload.TYPE, (payload, _) -> permissions = ServerPermission.fromFlags(payload.flags()));
        ClientConnectionEvents.DISCONNECT.register(() -> permissions = EnumSet.noneOf(ServerPermission.class));
    }

    public static boolean has(ServerPermission permission) {
        return permissions.contains(permission);
    }
}
