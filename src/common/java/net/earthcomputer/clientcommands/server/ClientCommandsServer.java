package net.earthcomputer.clientcommands.server;

import dev.xpple.betterconfig.api.ModConfigBuilder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class ClientCommandsServer implements ModInitializer {
    @Nullable
    private static volatile MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        new ModConfigBuilder<>(ServerConfigs.MOD_ID, ServerConfigs.class).build();

        PayloadTypeRegistry.serverboundPlay().register(OptInPayload.TYPE, OptInPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OptInPayload.TYPE, (_, _) -> {});
        PayloadTypeRegistry.clientboundPlay().register(PermissionsPayload.TYPE, PermissionsPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((listener, _, _) -> sendPermissions(listener.player));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> currentServer = null);

        ServerTaskQueue.register();
        ServerFindItem.register();
    }

    static void onPermissionsChanged() {
        // the config may be changed from the client thread in singleplayer
        executeOnServer(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendPermissions(player);
            }
        });
    }

    private static void sendPermissions(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, PermissionsPayload.TYPE)) {
            ServerPlayNetworking.send(player, new PermissionsPayload(ServerPermission.getAllowedFlags()));
        }
    }

    /**
     * Runs the given action on the server thread, if there is a server running.
     */
    private static void executeOnServer(Consumer<MinecraftServer> action) {
        MinecraftServer server = currentServer;
        if (server != null) {
            server.execute(() -> action.accept(server));
        }
    }

    public enum OptInPayload implements CustomPacketPayload {
        INSTANCE;

        public static final Type<OptInPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("clientcommands", "opt_in"));
        public static final StreamCodec<ByteBuf, OptInPayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OptInPayload> type() {
            return TYPE;
        }
    }
}
