package net.earthcomputer.clientcommands;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.QueryBlockNbtC2SPacket;
import net.minecraft.network.packet.c2s.play.QueryEntityNbtC2SPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class ClientcommandsDataQueryHandler {
    private final ClientPlayNetworkHandler networkHandler;
    private final List<CallbackEntry> callbacks = new ArrayList<>();

    public ClientcommandsDataQueryHandler(ClientPlayNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public static ClientcommandsDataQueryHandler get(ClientPlayNetworkHandler networkHandler) {
        return ((IClientPlayNetworkHandler) networkHandler).clientcommands_getCCDataQueryHandler();
    }

    public boolean handleQueryResponse(int transactionId, @Nullable NbtCompound nbt) {
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

    private int nextQuery(Consumer<@Nullable NbtCompound> callback) {
        int transactionId = ++networkHandler.getDataQueryHandler().expectedTransactionId;
        callbacks.add(new CallbackEntry(transactionId, callback));
        return transactionId;
    }

    public void queryEntityNbt(int entityNetworkId, Consumer<@Nullable NbtCompound> callback) {
        int transactionId = nextQuery(callback);
        networkHandler.sendPacket(new QueryEntityNbtC2SPacket(transactionId, entityNetworkId));
    }

    public void queryBlockNbt(BlockPos pos, Consumer<@Nullable NbtCompound> callback) {
        int transactionId = nextQuery(callback);
        networkHandler.sendPacket(new QueryBlockNbtC2SPacket(transactionId, pos));
    }

    private record CallbackEntry(int transactionId, Consumer<@Nullable NbtCompound> callback) {
    }

    public interface IClientPlayNetworkHandler {
        ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler();
    }
}
