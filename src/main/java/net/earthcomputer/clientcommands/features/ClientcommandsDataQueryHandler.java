package net.earthcomputer.clientcommands.features;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class ClientcommandsDataQueryHandler {
    private final ClientPacketListener packetListener;
    private final List<CallbackEntry> callbacks = new ArrayList<>();

    public ClientcommandsDataQueryHandler(ClientPacketListener packetListener) {
        this.packetListener = packetListener;
    }

    public static ClientcommandsDataQueryHandler get(ClientPacketListener packetListener) {
        return ((IClientPlayNetworkHandler) packetListener).clientcommands_getCCDataQueryHandler();
    }

    public boolean handleQueryResponse(int transactionId, @Nullable CompoundTag nbt) {
        Iterator<CallbackEntry> callbacks = this.callbacks.iterator();
        while (callbacks.hasNext()) {
            CallbackEntry callback = callbacks.next();
            if (callback.transactionId - transactionId > 0) {
                break;
            }
            callbacks.remove();
            if (callback.transactionId == transactionId) {
                callback.callback.accept(nbt);
                return true;
            }
        }
        return false;
    }

    private int nextQuery(Consumer<@Nullable CompoundTag> callback) {
        int transactionId = ++packetListener.getDebugQueryHandler().transactionId;
        callbacks.add(new CallbackEntry(transactionId, callback));
        return transactionId;
    }

    public void queryEntityNbt(int entityNetworkId, Consumer<@Nullable CompoundTag> callback) {
        int transactionId = nextQuery(callback);
        packetListener.send(new ServerboundEntityTagQueryPacket(transactionId, entityNetworkId));
    }

    public void queryBlockNbt(BlockPos pos, Consumer<@Nullable CompoundTag> callback) {
        int transactionId = nextQuery(callback);
        packetListener.send(new ServerboundBlockEntityTagQueryPacket(transactionId, pos));
    }

    private record CallbackEntry(int transactionId, Consumer<@Nullable CompoundTag> callback) {
    }

    public interface IClientPlayNetworkHandler {
        ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler();
    }
}
