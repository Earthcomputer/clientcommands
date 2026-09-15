package net.earthcomputer.clientcommands.util;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.interfaces.IClientPacketListener;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public final class RoundTripFence {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClientPacketListener connection;
    private long nextSendRequestId = 0;
    private long nextReceiveRequestId = 0;
    private final Long2ObjectMap<CompletableFuture<Void>> fences = new Long2ObjectOpenHashMap<>();

    static {
        ClientConnectionEvents.SEND_PACKET.register((connection, packet) -> {
            if (packet instanceof ServerboundClientCommandPacket clientCommandPacket && clientCommandPacket.getAction() == ServerboundClientCommandPacket.Action.REQUEST_STATS) {
                getInstance(connection).onSendRequestStats();
            }
        });
    }

    public RoundTripFence(ClientPacketListener connection) {
        this.connection = connection;
    }

    public static RoundTripFence getInstance(ClientPacketListener connection) {
        return ((IClientPacketListener) connection).clientcommands_getRoundTripFence();
    }

    public CompletableFuture<Void> fence() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        fences.put(nextSendRequestId, future);
        connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        return future;
    }

    public void onSendRequestStats() {
        nextSendRequestId++;
    }

    public void onReceiveStats() {
        if (nextReceiveRequestId == nextSendRequestId) {
            LOGGER.warn("Received stats without request");
            return;
        }

        CompletableFuture<Void> fence = fences.remove(nextReceiveRequestId);
        if (fence != null) {
            fence.complete(null);
        }

        nextReceiveRequestId++;
    }
}
