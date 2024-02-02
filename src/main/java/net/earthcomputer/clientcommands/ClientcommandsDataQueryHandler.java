package net.earthcomputer.clientcommands;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class ClientcommandsDataQueryHandler {
    private final ClientPacketListener networkHandler;
    private final List<CallbackEntry> callbacks = new ArrayList<>();

    public ClientcommandsDataQueryHandler(ClientPacketListener networkHandler) {
        this.networkHandler = networkHandler;
    }

    public static ClientcommandsDataQueryHandler get(ClientPacketListener networkHandler) {
        return ((IClientPlayNetworkHandler) networkHandler).clientcommands_getCCDataQueryHandler();
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
        int transactionId = ++networkHandler.getDebugQueryHandler().transactionId;
        callbacks.add(new CallbackEntry(transactionId, callback));
        return transactionId;
    }

    public void queryEntityNbt(int entityNetworkId, Consumer<@Nullable CompoundTag> callback) {
        int transactionId = nextQuery(callback);
        networkHandler.send(new ServerboundEntityTagQuery(transactionId, entityNetworkId));
    }

    public void queryBlockNbt(BlockPos pos, Consumer<@Nullable CompoundTag> callback) {
        int transactionId = nextQuery(callback);
        networkHandler.send(new ServerboundBlockEntityTagQuery(transactionId, pos));
    }

    private record CallbackEntry(int transactionId, Consumer<@Nullable CompoundTag> callback) {
    }

    public interface IClientPlayNetworkHandler {
        ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler();
    }
}
