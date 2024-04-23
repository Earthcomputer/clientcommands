package net.earthcomputer.clientcommands.features;

import com.mojang.brigadier.suggestion.Suggestions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.concurrent.CompletableFuture;

public final class SuggestionsHook {
    private SuggestionsHook() {
    }

    private static final int MAGIC_SUGGESTION_ID = -314159265;
    private static int currentSuggestionId = MAGIC_SUGGESTION_ID;
    private static final Int2ObjectMap<CompletableFuture<Suggestions>> pendingSuggestions = new Int2ObjectOpenHashMap<>();

    public static CompletableFuture<Void> fence() {
        return request("").thenAccept(suggestions -> {});
    }

    public static CompletableFuture<Suggestions> request(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Suggestions.empty();
        }

        currentSuggestionId--;
        CompletableFuture<Suggestions> future = new CompletableFuture<>();
        pendingSuggestions.put(currentSuggestionId, future);
        connection.send(new ServerboundCommandSuggestionPacket(currentSuggestionId, command));
        return future;
    }

    public static boolean onCompletions(ClientboundCommandSuggestionsPacket packet) {
        CompletableFuture<Suggestions> future = pendingSuggestions.remove(packet.id());
        if (future == null) {
            return false;
        }

        if (pendingSuggestions.isEmpty()) {
            currentSuggestionId = MAGIC_SUGGESTION_ID;
        }

        future.complete(packet.toSuggestions());
        return true;
    }

    public static void onDisconnect() {
        for (CompletableFuture<Suggestions> future : pendingSuggestions.values()) {
            future.complete(Suggestions.empty().join());
        }
        pendingSuggestions.clear();
        currentSuggestionId = MAGIC_SUGGESTION_ID;
    }
}
